package com.foldfinance.core.data

import com.foldfinance.core.data.db.FoldFinanceDatabase
import com.foldfinance.core.data.ingest.IngestOutcome
import com.foldfinance.core.data.ingest.IngestRepositoryImpl
import com.foldfinance.core.data.repository.BalanceRepositoryImpl
import com.foldfinance.core.data.repository.CardRepositoryImpl
import com.foldfinance.core.data.repository.TransactionRepositoryImpl
import com.foldfinance.core.model.DefaultCategories
import com.foldfinance.core.model.ParseStatus
import com.foldfinance.core.model.Source
import com.foldfinance.parser.Direction
import com.foldfinance.parser.Kind
import com.foldfinance.parser.ParseResult
import com.foldfinance.parser.ParsedBalance
import com.foldfinance.parser.ParserRegistry
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
class IngestRepositoryTest {

    private lateinit var db: FoldFinanceDatabase
    private val clock = fixedClock()

    private val purchaseSms = "SMS: покупка 500 Pyaterochka"
    private val purchasePush = "PUSH: покупка 500 Пятёрочка"

    @Before fun setUp() { db = inMemoryDb() }
    @After fun tearDown() { db.close() }

    private fun ingest(registry: ParserRegistry) = IngestRepositoryImpl(db, registry, clock)
    private val transactions get() = TransactionRepositoryImpl(db, clock)

    @Test
    fun smsAndPushOfSameOperation_produceOneTransactionWithBothRawMessages() = runTest {
        val repo = ingest(
            registryOf(
                purchaseSms to parsed(op(50_000, merchant = null, occurredAt = T0)),
                purchasePush to parsed(op(50_000, merchant = "Пятёрочка")),
            ),
        )

        val first = repo.ingest(sms(1, purchaseSms, T0))
        val second = repo.ingest(push("k1", purchasePush, T0.plusSeconds(90)))

        assertThat(first).isInstanceOf(IngestOutcome.Recorded::class.java)
        second as IngestOutcome.Recorded
        assertThat(second.createdCount).isEqualTo(0)
        assertThat(second.mergedCount).isEqualTo(1)

        val all = transactions.observe().first()
        assertThat(all).hasSize(1)
        val tx = all.single()
        assertThat(tx.source).isEqualTo(Source.SMS)
        // Push дополнил мерчанта, которого не было в SMS.
        assertThat(tx.merchant).isEqualTo("Пятёрочка")

        val raws = transactions.observeRawMessages(tx.id).first()
        assertThat(raws.map { it.channel.name }).containsExactly("SMS", "PUSH")
        assertThat(raws.map { it.transactionId }.toSet()).containsExactly(tx.id)
    }

    @Test
    fun crossSourceDedup_respectsWindowCardAndChannel() = runTest {
        val repo = ingest(
            registryOf(
                "sms-a" to parsed(op(50_000)),
                "push-late" to parsed(op(50_000)),
                "push-other-card" to parsed(op(50_000, cardMask = "*9999")),
                "sms-b" to parsed(op(50_000)),
            ),
        )
        repo.ingest(sms(1, "sms-a", T0))
        // Вне окна ±3 мин — отдельная операция.
        repo.ingest(push("late", "push-late", T0.plus(Duration.ofMinutes(4))))
        // Другая карта — отдельная операция.
        repo.ingest(push("other", "push-other-card", T0.plusSeconds(30)))
        // Тот же канал (две одинаковые SMS) — это две покупки, не дубль.
        repo.ingest(sms(2, "sms-b", T0.plusSeconds(20)))

        assertThat(transactions.observe().first()).hasSize(4)
    }

    @Test
    fun repeatedImport_isIdempotent() = runTest {
        val repo = ingest(
            registryOf(
                purchaseSms to parsed(op(50_000)),
                purchasePush to parsed(op(50_000)),
            ),
        )
        val smsMsg = sms(1, purchaseSms, T0)
        val pushMsg = push("k1", purchasePush, T0.plusSeconds(10))

        repo.ingest(smsMsg)
        repo.ingest(pushMsg)
        assertThat(repo.ingest(smsMsg)).isInstanceOf(IngestOutcome.AlreadyProcessed::class.java)
        assertThat(repo.ingest(pushMsg)).isInstanceOf(IngestOutcome.AlreadyProcessed::class.java)
        // То же SMS под другим _id (восстановление телефона): то же содержимое и время.
        assertThat(repo.ingest(smsMsg.copy(sourceKey = "777"))).isInstanceOf(IngestOutcome.AlreadyProcessed::class.java)

        assertThat(transactions.observe().first()).hasSize(1)
        assertThat(db.rawMessageDao().countStale(Int.MAX_VALUE)).isEqualTo(2)
    }

    @Test
    fun refund_isLinkedToPurchaseOfSameMerchantAndCard_andTakesItsCategory() = runTest {
        val repo = ingest(
            registryOf(
                "buy" to parsed(op(120_000, merchant = "OZON", occurredAt = T0)),
                "other-buy" to parsed(op(120_000, merchant = "WB", occurredAt = T0.plusSeconds(60))),
                "refund" to parsed(
                    op(120_000, direction = Direction.REFUND, kind = Kind.REFUND, merchant = "ozon", occurredAt = T0.plus(Duration.ofDays(10))),
                ),
            ),
        )
        val buy = repo.ingest(sms(1, "buy", T0)) as IngestOutcome.Recorded
        repo.ingest(sms(2, "other-buy", T0.plusSeconds(60)))
        val purchaseId = buy.transactionIds.single()
        val customCategory = db.categoryDao().getByKey("shopping")!!.id
        transactions.setCategory(purchaseId, customCategory)

        val refund = repo.ingest(sms(3, "refund", T0.plus(Duration.ofDays(10)))) as IngestOutcome.Recorded
        val refundTx = transactions.observeById(refund.transactionIds.single()).first()!!

        assertThat(refundTx.direction).isEqualTo(Direction.REFUND)
        assertThat(refundTx.linkedTransactionId).isEqualTo(purchaseId)
        assertThat(refundTx.categoryId).isEqualTo(customCategory)
    }

    @Test
    fun refund_olderThan90Days_isNotLinked() = runTest {
        val repo = ingest(
            registryOf(
                "buy" to parsed(op(10_000, merchant = "Shop", occurredAt = T0)),
                "refund" to parsed(
                    op(10_000, direction = Direction.REFUND, kind = Kind.REFUND, merchant = "Shop", occurredAt = T0.plus(Duration.ofDays(91))),
                ),
            ),
        )
        repo.ingest(sms(1, "buy", T0))
        val refund = repo.ingest(sms(2, "refund", T0.plus(Duration.ofDays(91)))) as IngestOutcome.Recorded
        val tx = transactions.observeById(refund.transactionIds.single()).first()!!
        assertThat(tx.direction).isEqualTo(Direction.REFUND)
        assertThat(tx.linkedTransactionId).isNull()
    }

    @Test
    fun transferToOwnCard_isInternalTransfer() = runTest {
        val cards = CardRepositoryImpl(db, clock)
        cards.setOwn("*1234", true)
        cards.setOwn("*5678", true)
        val repo = ingest(
            registryOf(
                "own" to parsed(op(300_000, kind = Kind.TRANSFER, cardMask = "*1234", counterpartyCardMask = "*5678")),
                "foreign" to parsed(op(70_000, kind = Kind.TRANSFER, cardMask = "*1234", counterpartyCardMask = "*0000")),
                "own-unknown-source" to parsed(op(5_000, kind = Kind.TRANSFER, cardMask = null, counterpartyCardMask = "*5678")),
            ),
        )
        val own = repo.ingest(sms(1, "own", T0)) as IngestOutcome.Recorded
        val foreign = repo.ingest(sms(2, "foreign", T0.plusSeconds(600))) as IngestOutcome.Recorded
        val third = repo.ingest(sms(3, "own-unknown-source", T0.plusSeconds(1200))) as IngestOutcome.Recorded

        suspend fun dir(id: Long) = transactions.observeById(id).first()!!.direction
        assertThat(dir(own.transactionIds.single())).isEqualTo(Direction.INTERNAL_TRANSFER)
        assertThat(dir(foreign.transactionIds.single())).isEqualTo(Direction.EXPENSE)
        assertThat(dir(third.transactionIds.single())).isEqualTo(Direction.INTERNAL_TRANSFER)
    }

    @Test
    fun markingCardOwnLater_reclassifiesPastTransfers_exceptManualDirection() = runTest {
        val cards = CardRepositoryImpl(db, clock)
        val repo = ingest(
            registryOf(
                "t1" to parsed(op(100_000, kind = Kind.TRANSFER, counterpartyCardMask = "*5678")),
                "t2" to parsed(op(200_000, kind = Kind.TRANSFER, counterpartyCardMask = "*5678")),
            ),
        )
        val t1 = (repo.ingest(sms(1, "t1", T0)) as IngestOutcome.Recorded).transactionIds.single()
        val t2 = (repo.ingest(sms(2, "t2", T0.plusSeconds(600))) as IngestOutcome.Recorded).transactionIds.single()
        // Правка заметки не мешает пересчёту; ручная смена направления — мешает.
        transactions.update(transactions.observeById(t1).first()!!.copy(note = "себе"))
        val edited = transactions.observeById(t2).first()!!
        transactions.update(edited.copy(note = "долг", direction = Direction.INCOME))

        cards.setOwn("*5678", true)
        assertThat(transactions.observeById(t1).first()!!.direction).isEqualTo(Direction.INTERNAL_TRANSFER)
        assertThat(transactions.observeById(t2).first()!!.direction).isEqualTo(Direction.INCOME)

        cards.setOwn("*5678", false)
        assertThat(transactions.observeById(t1).first()!!.direction).isEqualTo(Direction.EXPENSE)
    }

    @Test
    fun lowConfidenceAndUncertain_goToReviewQueue() = runTest {
        val repo = ingest(
            registryOf(
                "low" to parsed(op(1_000, confidence = 0.5f)),
                "high" to parsed(op(2_000, confidence = 0.9f)),
                "uncertain" to ParseResult.Uncertain(reason = "kind", partial = op(3_000, confidence = 0.9f), parserId = "x"),
                "nothing" to ParseResult.Uncertain(reason = "amount", parserId = "x"),
            ),
        )
        assertThat((repo.ingest(sms(1, "low", T0)) as IngestOutcome.Recorded).needsReview).isTrue()
        assertThat((repo.ingest(sms(2, "high", T0.plusSeconds(600))) as IngestOutcome.Recorded).needsReview).isFalse()
        assertThat((repo.ingest(sms(3, "uncertain", T0.plusSeconds(1200))) as IngestOutcome.Recorded).needsReview).isTrue()
        val nothing = repo.ingest(sms(4, "nothing", T0.plusSeconds(1800)))
        assertThat(nothing).isInstanceOf(IngestOutcome.Unrecognized::class.java)
        assertThat(db.rawMessageDao().getById((nothing as IngestOutcome.Unrecognized).rawMessageId)!!.parseStatus)
            .isEqualTo(ParseStatus.UNCERTAIN)

        val queue = transactions.observeReviewQueue().first()
        assertThat(queue.map { it.amountMinor }).containsExactly(3_000L, 1_000L).inOrder()
        assertThat(transactions.observeReviewCount().first()).isEqualTo(2)
    }

    @Test
    fun notFinancial_storesRawMessageOnly() = runTest {
        val repo = ingest(registryOf())
        val outcome = repo.ingest(sms(1, "Код подтверждения 1234", T0))
        assertThat(outcome).isInstanceOf(IngestOutcome.NotFinancial::class.java)
        assertThat(transactions.observe().first()).isEmpty()
    }

    @Test
    fun categoryFromCategorizer_isAssigned() = runTest {
        val repo = ingest(registryOf("buy" to parsed(op(1_000, merchant = "X"))))
        val id = (repo.ingest(sms(1, "buy", T0)) as IngestOutcome.Recorded).transactionIds.single()
        val other = db.categoryDao().getByKey(DefaultCategories.OTHER_KEY)!!.id
        assertThat(transactions.observeById(id).first()!!.categoryId).isEqualTo(other)
    }

    @Test
    fun knownBalance_latestPerCard() = runTest {
        val repo = ingest(
            registryOf(
                "b1" to parsed(op(1_000), balance = ParsedBalance(900_000, "RUB", "*1234")),
                "b2" to parsed(op(2_000), balance = ParsedBalance(880_000, "RUB", null)),
                "b3" to parsed(op(3_000, cardMask = "*5678"), balance = ParsedBalance(10_000, "RUB", "*5678")),
            ),
        )
        repo.ingest(sms(1, "b1", T0))
        repo.ingest(sms(3, "b3", T0.plusSeconds(1200)))
        // Пришло позже по времени получения — становится последним; маска берётся из операции.
        repo.ingest(sms(2, "b2", T0.plusSeconds(600)))

        val latest = BalanceRepositoryImpl(db).observeLatest().first()
        assertThat(latest.map { it.cardMask to it.amountMinor })
            .containsExactly("*1234" to 880_000L, "*5678" to 10_000L).inOrder()
        assertThat(latest.first().asOf).isEqualTo(T0.plusSeconds(600))
    }

    @Test
    fun feeIsSeparateOperation_andCardsAreRegistered() = runTest {
        val repo = ingest(
            registryOf("with-fee" to parsed(op(100_000, kind = Kind.TRANSFER), op(1_000, kind = Kind.FEE))),
        )
        val outcome = repo.ingest(sms(1, "with-fee", T0)) as IngestOutcome.Recorded
        assertThat(outcome.createdCount).isEqualTo(2)
        val all = transactions.observe().first()
        assertThat(all.map { it.dedupKey }).containsExactly("SMS:1#0", "SMS:1#1")
        assertThat(CardRepositoryImpl(db, clock).observeAll().first().map { it.mask to it.isOwn })
            .containsExactly("*1234" to false)
    }
}
