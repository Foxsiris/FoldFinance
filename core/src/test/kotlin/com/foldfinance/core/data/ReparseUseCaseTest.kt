package com.foldfinance.core.data

import com.foldfinance.core.data.db.FoldFinanceDatabase
import com.foldfinance.core.data.ingest.IngestOutcome
import com.foldfinance.core.data.ingest.IngestRepositoryImpl
import com.foldfinance.core.data.ingest.ReparseUseCase
import com.foldfinance.core.data.repository.TransactionRepositoryImpl
import com.foldfinance.core.model.ParseStatus
import com.foldfinance.parser.Direction
import com.foldfinance.parser.Kind
import com.foldfinance.parser.PARSER_VERSION
import com.foldfinance.parser.ParseResult
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReparseUseCaseTest {

    private lateinit var db: FoldFinanceDatabase
    private val clock = fixedClock()
    private val transactions get() = TransactionRepositoryImpl(db, clock)

    @Before fun setUp() { db = inMemoryDb() }
    @After fun tearDown() { db.close() }

    /** Имитировать, что сообщение разобрано старой версией парсера. */
    private suspend fun markStale(rawId: Long) {
        val raw = db.rawMessageDao().getById(rawId)!!
        db.rawMessageDao().update(raw.copy(parserVersion = PARSER_VERSION - 1))
    }

    @Test
    fun reparse_createsOperationsForPreviouslyUnrecognizedMessages() = runTest {
        val old = registryOf("msg" to ParseResult.Uncertain(reason = "not_implemented", parserId = "old"))
        val unrecognized = IngestRepositoryImpl(db, old, clock).ingest(sms(1, "msg", T0)) as IngestOutcome.Unrecognized
        markStale(unrecognized.rawMessageId)

        val new = registryOf("msg" to parsed(op(42_00, merchant = "Кафе")))
        val useCase = ReparseUseCase(db, new, clock)
        assertThat(useCase.pendingCount()).isEqualTo(1)
        val report = useCase()

        assertThat(report.messages).isEqualTo(1)
        assertThat(report.created).isEqualTo(1)
        assertThat(useCase.pendingCount()).isEqualTo(0)
        val raw = db.rawMessageDao().getById(unrecognized.rawMessageId)!!
        assertThat(raw.parserVersion).isEqualTo(PARSER_VERSION)
        assertThat(raw.parseStatus).isEqualTo(ParseStatus.PARSED)
        assertThat(raw.transactionId).isNotNull()
        assertThat(transactions.observe().first().single().merchant).isEqualTo("Кафе")

        // Повторный запуск ничего не делает.
        assertThat(useCase().messages).isEqualTo(0)
        assertThat(transactions.observe().first()).hasSize(1)
    }

    @Test
    fun reparse_updatesParsedFields_butNeverUserEditedOnes() = runTest {
        val v1 = registryOf(
            "a" to parsed(op(100_00, merchant = "SHOP A", confidence = 0.5f)),
            "b" to parsed(op(200_00, merchant = "SHOP B", confidence = 0.5f)),
        )
        val ingest = IngestRepositoryImpl(db, v1, clock)
        val a = ingest.ingest(sms(1, "a", T0)) as IngestOutcome.Recorded
        val b = ingest.ingest(sms(2, "b", T0.plusSeconds(600))) as IngestOutcome.Recorded
        val bTx = transactions.observeById(b.transactionIds.single()).first()!!
        transactions.update(bTx.copy(merchant = "Мой магазин", amountMinor = 199_00, needsReview = false))
        markStale(a.rawMessageId)
        markStale(b.rawMessageId)

        val v2 = registryOf(
            "a" to parsed(op(100_00, merchant = "Shop A", kind = Kind.PURCHASE, confidence = 0.95f)),
            "b" to parsed(op(200_00, merchant = "Shop B", direction = Direction.INCOME, confidence = 0.95f)),
        )
        val report = ReparseUseCase(db, v2, clock)()
        assertThat(report.messages).isEqualTo(2)
        assertThat(report.updated).isEqualTo(1)

        val aTx = transactions.observeById(a.transactionIds.single()).first()!!
        assertThat(aTx.merchant).isEqualTo("Shop A")
        assertThat(aTx.needsReview).isFalse()

        val bAfter = transactions.observeById(b.transactionIds.single()).first()!!
        assertThat(bAfter.merchant).isEqualTo("Мой магазин")
        assertThat(bAfter.amountMinor).isEqualTo(199_00)
        assertThat(bAfter.direction).isEqualTo(Direction.EXPENSE)
        assertThat(bAfter.userEdited).isTrue()
        assertThat(db.rawMessageDao().getById(b.rawMessageId)!!.parserVersion).isEqualTo(PARSER_VERSION)
    }

    @Test
    fun reparse_doesNotResurrectDeletedOperation_norDuplicateMergedOne() = runTest {
        val v1 = registryOf(
            "sms" to parsed(op(500_00)),
            "push" to parsed(op(500_00)),
            "deleted" to parsed(op(700_00)),
        )
        val ingest = IngestRepositoryImpl(db, v1, clock)
        val s = ingest.ingest(sms(1, "sms", T0)) as IngestOutcome.Recorded
        val p = ingest.ingest(push("k", "push", T0.plusSeconds(60))) as IngestOutcome.Recorded
        val d = ingest.ingest(sms(2, "deleted", T0.plusSeconds(3600))) as IngestOutcome.Recorded
        transactions.delete(d.transactionIds.single())
        listOf(s.rawMessageId, p.rawMessageId, d.rawMessageId).forEach { markStale(it) }

        ReparseUseCase(db, v1, clock)()

        val all = transactions.observe().first()
        assertThat(all.map { it.amountMinor }).containsExactly(500_00L)
        assertThat(transactions.observeRawMessages(all.single().id).first()).hasSize(2)
    }

    @Test
    fun reparse_operationNoLongerFound_goesToReviewInsteadOfDeletion() = runTest {
        val v1 = registryOf("m" to parsed(op(10_00)))
        val r = IngestRepositoryImpl(db, v1, clock).ingest(sms(1, "m", T0)) as IngestOutcome.Recorded
        markStale(r.rawMessageId)

        ReparseUseCase(db, registryOf(), clock)()

        val tx = transactions.observeById(r.transactionIds.single()).first()!!
        assertThat(tx.needsReview).isTrue()
        assertThat(db.rawMessageDao().getById(r.rawMessageId)!!.parseStatus).isEqualTo(ParseStatus.NOT_FINANCIAL)
    }
}
