package com.foldfinance.core.data

import app.cash.turbine.test
import com.foldfinance.core.data.db.FoldFinanceDatabase
import com.foldfinance.core.data.ingest.IngestOutcome
import com.foldfinance.core.data.ingest.IngestRepositoryImpl
import com.foldfinance.core.data.repository.ManualTransactionInput
import com.foldfinance.core.data.repository.MerchantRuleRepositoryImpl
import com.foldfinance.core.data.repository.TransactionFilter
import com.foldfinance.core.data.repository.TransactionRepositoryImpl
import com.foldfinance.core.data.repository.TransactionSort
import com.foldfinance.core.model.Source
import com.foldfinance.parser.Direction
import com.foldfinance.parser.Kind
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
class TransactionRepositoryTest {

    private lateinit var db: FoldFinanceDatabase
    private lateinit var repo: TransactionRepositoryImpl
    private val clock = fixedClock()

    private var groceries = 0L
    private var transport = 0L

    @Before
    fun setUp() = runBlocking {
        db = inMemoryDb()
        repo = TransactionRepositoryImpl(db, clock)
        groceries = db.categoryDao().getByKey("groceries")!!.id
        transport = db.categoryDao().getByKey("transport")!!.id
    }

    @After fun tearDown() { db.close() }

    private suspend fun add(
        amount: Long,
        daysAgo: Long,
        direction: Direction = Direction.EXPENSE,
        merchant: String? = null,
        category: Long? = null,
        note: String? = null,
        currency: String = "RUB",
    ): Long = repo.addManual(
        ManualTransactionInput(
            occurredAt = T0.minus(Duration.ofDays(daysAgo)),
            amountMinor = amount,
            currency = currency,
            direction = direction,
            merchant = merchant,
            categoryId = category,
            note = note,
        ),
    )

    private suspend fun amounts(filter: TransactionFilter) = repo.observe(filter).first().map { it.amountMinor }

    @Test
    fun observe_filtersByPeriodCategoryDirectionAmountQuery_andSorts() = runTest {
        add(100, daysAgo = 1, merchant = "ПЯТЁРОЧКА", category = groceries)
        add(250, daysAgo = 2, merchant = "Metro", category = transport)
        add(5_000, daysAgo = 3, direction = Direction.INCOME, note = "зарплата")
        add(900, daysAgo = 40, merchant = "Магнит", category = groceries)
        add(300, daysAgo = 5, direction = Direction.REFUND, merchant = "Пятерочка", category = groceries)
        add(700, daysAgo = 6, currency = "USD", merchant = "Amazon")

        assertThat(amounts(TransactionFilter())).containsExactly(100L, 250L, 5_000L, 300L, 700L, 900L).inOrder()
        assertThat(amounts(TransactionFilter(from = T0.minus(Duration.ofDays(3)), to = T0.minus(Duration.ofDays(1)))))
            .containsExactly(250L, 5_000L).inOrder()
        assertThat(amounts(TransactionFilter(categoryIds = setOf(groceries)))).containsExactly(100L, 300L, 900L)
        assertThat(amounts(TransactionFilter(categoryIds = setOf(transport), includeUncategorized = true)))
            .containsExactly(250L, 5_000L, 700L)
        assertThat(amounts(TransactionFilter(categoryIds = emptySet()))).isEmpty()
        assertThat(amounts(TransactionFilter(directions = setOf(Direction.INCOME, Direction.REFUND))))
            .containsExactly(5_000L, 300L)
        assertThat(amounts(TransactionFilter(minAmountMinor = 250, maxAmountMinor = 900, currency = "RUB")))
            .containsExactly(250L, 300L, 900L)
        // Поиск без учёта регистра и ё/е, по мерчанту и заметке.
        assertThat(amounts(TransactionFilter(query = "пятерочка"))).containsExactly(100L, 300L)
        assertThat(amounts(TransactionFilter(query = "ЗАРПЛ"))).containsExactly(5_000L)
        assertThat(amounts(TransactionFilter(query = "100%"))).isEmpty()
        assertThat(amounts(TransactionFilter(sort = TransactionSort.AMOUNT_DESC)))
            .containsExactly(5_000L, 900L, 700L, 300L, 250L, 100L).inOrder()
        assertThat(amounts(TransactionFilter(sort = TransactionSort.DATE_ASC)).first()).isEqualTo(900L)
    }

    @Test
    fun observe_emitsOnChanges() = runTest {
        repo.observe(TransactionFilter(directions = setOf(Direction.EXPENSE))).test {
            assertThat(awaitItem()).isEmpty()
            add(100, daysAgo = 1)
            assertThat(awaitItem().map { it.amountMinor }).containsExactly(100L)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun update_setsUserEdited_andKeepsSourceFields() = runTest {
        val ingest = IngestRepositoryImpl(db, registryOf("m" to parsed(op(1_000, confidence = 0.4f))), clock)
        val id = (ingest.ingest(sms(1, "m", T0)) as IngestOutcome.Recorded).transactionIds.single()
        val tx = repo.observeById(id).first()!!
        assertThat(tx.userEdited).isFalse()

        repo.update(tx.copy(amountMinor = 1_500, kind = Kind.FEE, dedupKey = "hacked", source = Source.MANUAL, needsReview = false))

        val after = repo.observeById(id).first()!!
        assertThat(after.amountMinor).isEqualTo(1_500)
        assertThat(after.kind).isEqualTo(Kind.FEE)
        assertThat(after.userEdited).isTrue()
        assertThat(after.dedupKey).isEqualTo("SMS:1#0")
        assertThat(after.source).isEqualTo(Source.SMS)
    }

    @Test
    fun reviewQueue_confirmAndBulkCategory() = runTest {
        val ingest = IngestRepositoryImpl(
            db,
            registryOf(
                "a" to parsed(op(1_000, confidence = 0.3f)),
                "b" to parsed(op(2_000, confidence = 0.3f)),
                "c" to parsed(op(3_000, confidence = 0.3f)),
            ),
            clock,
        )
        val ids = listOf("a", "b", "c").mapIndexed { i, text ->
            (ingest.ingest(sms(i.toLong(), text, T0.plusSeconds(600L * i))) as IngestOutcome.Recorded).transactionIds.single()
        }
        assertThat(repo.observeReviewCount().first()).isEqualTo(3)

        repo.confirmReview(ids[0])
        repo.setCategory(ids.drop(1), transport)
        assertThat(repo.observeReviewCount().first()).isEqualTo(2)
        repo.confirmReview(ids.drop(1))
        assertThat(repo.observeReviewQueue().first()).isEmpty()

        val categorized = ids.drop(1).map { repo.observeById(it).first()!! }
        assertThat(categorized.map { it.categoryId }.toSet()).containsExactly(transport)
        assertThat(categorized.all { it.userEdited }).isTrue()
    }

    @Test
    fun delete_removesTransaction_andReimportDoesNotRestoreIt() = runTest {
        val ingest = IngestRepositoryImpl(db, registryOf("m" to parsed(op(1_000))), clock)
        val message = sms(1, "m", T0)
        val id = (ingest.ingest(message) as IngestOutcome.Recorded).transactionIds.single()

        repo.delete(id)
        assertThat(repo.observeById(id).first()).isNull()
        assertThat(ingest.ingest(message)).isInstanceOf(IngestOutcome.AlreadyProcessed::class.java)
        assertThat(repo.observe().first()).isEmpty()
    }

    @Test
    fun merchantRule_appliesToPastOperations_exceptUserEdited() = runTest {
        val a = add(100, daysAgo = 1, merchant = "YANDEX*GO Taxi")
        val b = add(200, daysAgo = 2, merchant = "Яндекс Go")
        val c = add(300, daysAgo = 3, merchant = "yandex*go")
        // Ручной ввод — тоже правка пользователя; для проверки берём операцию из SMS.
        val ingest = IngestRepositoryImpl(db, registryOf("m" to parsed(op(400, merchant = "YANDEX*GO"))), clock)
        val fromSms = (ingest.ingest(sms(1, "m", T0)) as IngestOutcome.Recorded).transactionIds.single()

        val result = MerchantRuleRepositoryImpl(db, clock).add("Yandex*Go", transport)

        assertThat(result.updatedTransactions).isEqualTo(1)
        assertThat(repo.observeById(fromSms).first()!!.categoryId).isEqualTo(transport)
        listOf(a, b, c).forEach { assertThat(repo.observeById(it).first()!!.categoryId).isNull() }
    }
}
