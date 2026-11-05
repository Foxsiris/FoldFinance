package com.foldfinance.core.data

import com.foldfinance.core.data.db.FoldFinanceDatabase
import com.foldfinance.core.data.ingest.IncomingMessage
import com.foldfinance.core.data.ingest.IngestOutcome
import com.foldfinance.core.data.ingest.IngestRepositoryImpl
import com.foldfinance.core.data.ingest.ReparseUseCase
import com.foldfinance.core.data.ingest.SourceKeys
import com.foldfinance.core.data.repository.CardRepositoryImpl
import com.foldfinance.core.data.repository.TransactionRepositoryImpl
import com.foldfinance.core.data.repository.UnrecognizedMessagesRepositoryImpl
import com.foldfinance.core.model.ParseStatus
import com.foldfinance.parser.Direction
import com.foldfinance.parser.Kind
import com.foldfinance.parser.MessageChannel
import com.foldfinance.parser.PARSER_VERSION
import com.foldfinance.parser.ParseResult
import com.foldfinance.parser.ParserRegistry
import com.foldfinance.parser.UncertainReasons
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

/** Регрессии по находкам ревью: ключ SMS, перепарсинг, «моя карта», слияние, «Не распознано», сбои. */
@RunWith(RobolectricTestRunner::class)
class ReviewFindingsTest {

    private lateinit var db: FoldFinanceDatabase
    private val clock = fixedClock()
    private val transactions get() = TransactionRepositoryImpl(db, clock)
    private val unrecognized get() = UnrecognizedMessagesRepositoryImpl(db)

    @Before fun setUp() { db = inMemoryDb() }
    @After fun tearDown() { db.close() }

    private fun ingest(registry: ParserRegistry) = IngestRepositoryImpl(db, registry, clock)

    private fun smsKeyed(id: Long, text: String, at: Instant, key: String = SourceKeys.sms(id, at.toEpochMilli(), text)) =
        IncomingMessage(MessageChannel.SMS, "900", key, at, text)

    private suspend fun markStale(rawId: Long) {
        val raw = db.rawMessageDao().getById(rawId)!!
        db.rawMessageDao().update(raw.copy(parserVersion = PARSER_VERSION - 1))
    }

    private suspend fun tx(id: Long) = transactions.observeById(id).first()!!

    // ------------------------------------------------------------------ 1. SMS _id reuse

    @Test
    fun reusedSmsId_withAnotherMessage_isNotTakenForAlreadyProcessed() = runTest {
        val repo = ingest(registryOf("a" to parsed(op(100_00)), "b" to parsed(op(200_00))))
        repo.ingest(smsKeyed(5, "a", T0)) as IngestOutcome.Recorded

        // SMS 5 удалена, следующая получила тот же _id.
        val second = repo.ingest(smsKeyed(5, "b", T0.plusSeconds(3_600)))

        assertThat(second).isInstanceOf(IngestOutcome.Recorded::class.java)
        assertThat(transactions.observe().first().map { it.amountMinor }).containsExactly(100_00L, 200_00L)
    }

    @Test
    fun legacyKeyedSms_isFoundByContent_andMovedToNewKey() = runTest {
        val repo = ingest(registryOf("a" to parsed(op(100_00))))
        val legacy = repo.ingest(smsKeyed(5, "a", T0, key = "5")) as IngestOutcome.Recorded
        val newKey = SourceKeys.sms(5, T0.toEpochMilli(), "a")
        assertThat(repo.isKnown(MessageChannel.SMS, newKey)).isFalse()

        val again = repo.ingest(smsKeyed(5, "a", T0))

        assertThat(again).isEqualTo(IngestOutcome.AlreadyProcessed(legacy.rawMessageId))
        assertThat(repo.isKnown(MessageChannel.SMS, newKey)).isTrue()
        assertThat(transactions.observe().first()).hasSize(1)
    }

    // ------------------------------------------------------------------ 2. Confirmation survives reparse

    @Test
    fun confirmedOperation_staysOutOfReview_afterReparse_untilParsedValuesChange() = runTest {
        val v1 = registryOf("a" to parsed(op(100_00, merchant = "SHOP", confidence = 0.5f)))
        val rec = ingest(v1).ingest(smsKeyed(1, "a", T0)) as IngestOutcome.Recorded
        val id = rec.transactionIds.single()
        assertThat(tx(id).needsReview).isTrue()

        transactions.confirmReview(listOf(id))
        markStale(rec.rawMessageId)
        ReparseUseCase(db, v1, clock)()
        assertThat(tx(id).needsReview).isFalse()

        // Новая версия парсера читает другую сумму — подтверждалась не она: снова на проверку.
        markStale(rec.rawMessageId)
        ReparseUseCase(db, registryOf("a" to parsed(op(90_00, merchant = "SHOP", confidence = 0.5f))), clock)()
        assertThat(tx(id).amountMinor).isEqualTo(90_00)
        assertThat(tx(id).needsReview).isTrue()
    }

    // ------------------------------------------------------------------ 3. Merchant from merged push survives reparse

    @Test
    fun reparseOfSms_keepsMerchantAndCategoryFromMergedPush() = runTest {
        val registry = registryOf(
            "sms" to parsed(op(500_00, merchant = null)),
            "push" to parsed(op(500_00, merchant = "PYATEROCHKA")),
        )
        val repo = ingest(registry)
        val sms = repo.ingest(smsKeyed(1, "sms", T0)) as IngestOutcome.Recorded
        val merged = repo.ingest(push("k1", "push", T0.plusSeconds(30))) as IngestOutcome.Recorded
        assertThat(merged.mergedCount).isEqualTo(1)
        val id = sms.transactionIds.single()

        markStale(sms.rawMessageId)
        ReparseUseCase(db, registry, clock)()

        val groceries = db.categoryDao().getByKey("groceries")!!.id
        assertThat(tx(id).merchant).isEqualTo("PYATEROCHKA")
        assertThat(tx(id).categoryId).isEqualTo(groceries)
    }

    // ------------------------------------------------------------------ 4. «Моя карта» with a recategorised transfer

    @Test
    fun setOwn_recomputesTransfer_whoseCategoryWasChanged_butNotManualDirection() = runTest {
        val registry = registryOf(
            "t1" to parsed(op(1_000_00, direction = Direction.EXPENSE, kind = Kind.TRANSFER, counterpartyCardMask = "*5678")),
            "t2" to parsed(op(2_000_00, direction = Direction.EXPENSE, kind = Kind.TRANSFER, counterpartyCardMask = "*5678")),
        )
        val repo = ingest(registry)
        val recategorised = (repo.ingest(smsKeyed(1, "t1", T0)) as IngestOutcome.Recorded).transactionIds.single()
        val redirected = (repo.ingest(smsKeyed(2, "t2", T0.plusSeconds(3_600))) as IngestOutcome.Recorded).transactionIds.single()
        val other = db.categoryDao().getByKey("other")?.id ?: db.categoryDao().getAll().first().id
        transactions.setCategory(listOf(recategorised), other)
        transactions.update(tx(redirected).copy(direction = Direction.INCOME))

        CardRepositoryImpl(db, clock).setOwn("*5678", isOwn = true)

        assertThat(tx(recategorised).direction).isEqualTo(Direction.INTERNAL_TRANSFER)
        assertThat(tx(recategorised).categoryId).isEqualTo(other)
        assertThat(tx(redirected).direction).isEqualTo(Direction.INCOME)
    }

    // ------------------------------------------------------------------ 5. Cross-source merge without masks

    @Test
    fun crossSourceMerge_withoutCardMasks_isMergedButSentToReview() = runTest {
        val repo = ingest(
            registryOf(
                "sms" to parsed(op(300_00, cardMask = null, merchant = "SHOP")),
                "push" to parsed(op(300_00, cardMask = null, merchant = "SHOP")),
            ),
        )
        val first = repo.ingest(smsKeyed(1, "sms", T0)) as IngestOutcome.Recorded
        assertThat(tx(first.transactionIds.single()).needsReview).isFalse()

        val second = repo.ingest(push("k1", "push", T0.plusSeconds(60))) as IngestOutcome.Recorded

        assertThat(second.mergedCount).isEqualTo(1)
        assertThat(second.needsReview).isTrue()
        assertThat(transactions.observe().first()).hasSize(1)
        assertThat(tx(first.transactionIds.single()).needsReview).isTrue()
    }

    @Test
    fun crossSourceMerge_withDifferentMerchants_isSentToReview_sameMerchantIsNot() = runTest {
        val repo = ingest(
            registryOf(
                "sms1" to parsed(op(300_00, merchant = "SHOP ONE")),
                "push1" to parsed(op(300_00, merchant = "CAFE TWO")),
                "sms2" to parsed(op(400_00, merchant = "PYATEROCHKA")),
                "push2" to parsed(op(400_00, merchant = "Pyaterochka")),
            ),
        )
        val differ = repo.ingest(smsKeyed(1, "sms1", T0)) as IngestOutcome.Recorded
        repo.ingest(push("k1", "push1", T0.plusSeconds(60)))
        val same = repo.ingest(smsKeyed(2, "sms2", T0.plusSeconds(3_600))) as IngestOutcome.Recorded
        repo.ingest(push("k2", "push2", T0.plusSeconds(3_660)))

        assertThat(transactions.observe().first()).hasSize(2)
        assertThat(tx(differ.transactionIds.single()).needsReview).isTrue()
        assertThat(tx(same.transactionIds.single()).needsReview).isFalse()
    }

    // ------------------------------------------------------------------ 6. «Не распознано»

    @Test
    fun uncertainWithoutOperation_isListedAsUnrecognized_andCanBeHiddenOrAttached() = runTest {
        val repo = ingest(
            registryOf(
                "x" to ParseResult.Uncertain(reason = UncertainReasons.AMOUNT_NOT_FOUND, parserId = "scripted"),
                "y" to ParseResult.Uncertain(reason = UncertainReasons.UNKNOWN_OPERATION, parserId = "scripted"),
                "ok" to parsed(op(100_00)),
                "partial" to ParseResult.Uncertain(reason = UncertainReasons.DIRECTION_UNKNOWN, partial = op(50_00), parserId = "scripted"),
            ),
        )
        val x = repo.ingest(smsKeyed(1, "x", T0)) as IngestOutcome.Unrecognized
        val y = repo.ingest(smsKeyed(2, "y", T0.plusSeconds(60))) as IngestOutcome.Unrecognized
        repo.ingest(smsKeyed(3, "ok", T0.plusSeconds(120)))
        repo.ingest(smsKeyed(4, "partial", T0.plusSeconds(180)))

        // Только сообщения без операции; новые сверху.
        assertThat(unrecognized.observeUnrecognized().first().map { it.id })
            .containsExactly(y.rawMessageId, x.rawMessageId).inOrder()

        unrecognized.hide(x.rawMessageId)
        val manual = transactions.addManual(
            com.foldfinance.core.data.repository.ManualTransactionInput(
                occurredAt = T0, amountMinor = 70_00, currency = "RUB", direction = Direction.EXPENSE, note = "y",
            ),
        )
        assertThat(unrecognized.attachToTransaction(y.rawMessageId, manual)).isTrue()

        assertThat(unrecognized.observeUnrecognized().first()).isEmpty()
        assertThat(transactions.observeRawMessages(manual).first().map { it.id }).containsExactly(y.rawMessageId)
    }

    // ------------------------------------------------------------------ 7/8. Failures

    @Test
    fun recordFailed_storesFailedMessage_onceAndVisibly() = runTest {
        val repo = ingest(registryOf())
        val message = smsKeyed(7, "broken", T0)

        val first = repo.recordFailed(message) as IngestOutcome.Unrecognized
        val second = repo.recordFailed(message)

        assertThat(first.reason).isEqualTo(UncertainReasons.PARSER_ERROR)
        assertThat(second).isEqualTo(IngestOutcome.AlreadyProcessed(first.rawMessageId))
        val raw = db.rawMessageDao().getById(first.rawMessageId)!!
        assertThat(raw.parseStatus).isEqualTo(ParseStatus.FAILED)
        assertThat(raw.parserVersion).isEqualTo(PARSER_VERSION)
        assertThat(unrecognized.observeUnrecognized().first().map { it.id }).containsExactly(first.rawMessageId)
    }

    @Test
    fun reparse_poisonMessage_failsWithoutSkip_andIsMarkedFailedWithSkip() = runTest {
        val v1 = registryOf(
            "poison" to ParseResult.Uncertain(reason = "old", parserId = "old"),
            "good" to ParseResult.Uncertain(reason = "old", parserId = "old"),
        )
        val repo = ingest(v1)
        val poison = repo.ingest(smsKeyed(1, "poison", T0)) as IngestOutcome.Unrecognized
        val good = repo.ingest(smsKeyed(2, "good", T0.plusSeconds(60))) as IngestOutcome.Unrecognized
        markStale(poison.rawMessageId)
        markStale(good.rawMessageId)
        // Запись разбора «ядовитого» сообщения всегда падает.
        db.openHelper.writableDatabase.execSQL(
            """
            CREATE TRIGGER poison BEFORE UPDATE ON raw_messages
            WHEN NEW.text = 'poison' AND NEW.parse_status = 'PARSED'
            BEGIN SELECT RAISE(ABORT, 'poison'); END
            """.trimIndent(),
        )
        val v2 = registryOf("poison" to parsed(op(10_00)), "good" to parsed(op(20_00)))

        val error = runCatching { ReparseUseCase(db, v2, clock)() }.exceptionOrNull()
        assertThat(error).isNotNull()

        val report = ReparseUseCase(db, v2, clock)(skipFailing = true)

        assertThat(report.failed).isEqualTo(1)
        assertThat(report.created).isEqualTo(1)
        val raw = db.rawMessageDao().getById(poison.rawMessageId)!!
        assertThat(raw.parseStatus).isEqualTo(ParseStatus.FAILED)
        assertThat(raw.parserVersion).isEqualTo(PARSER_VERSION)
        assertThat(ReparseUseCase(db, v2, clock).pendingCount()).isEqualTo(0)
        assertThat(transactions.observe().first().map { it.amountMinor }).containsExactly(20_00L)
    }
}
