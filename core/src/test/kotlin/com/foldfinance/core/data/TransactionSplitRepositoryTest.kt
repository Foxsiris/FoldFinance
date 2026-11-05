package com.foldfinance.core.data

import com.foldfinance.core.data.db.FoldFinanceDatabase
import com.foldfinance.core.data.repository.ManualTransactionInput
import com.foldfinance.core.data.repository.MerchantAliasRepositoryImpl
import com.foldfinance.core.data.repository.SplitPartInput
import com.foldfinance.core.data.repository.TransactionFilter
import com.foldfinance.core.data.repository.TransactionRepositoryImpl
import com.foldfinance.core.data.repository.TransactionSplitRepositoryImpl
import com.foldfinance.parser.Direction
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Разбиение по чеку (БД v4) и поиск по отображаемому имени мерчанта. */
@RunWith(RobolectricTestRunner::class)
class TransactionSplitRepositoryTest {

    private lateinit var db: FoldFinanceDatabase
    private lateinit var transactions: TransactionRepositoryImpl
    private lateinit var splits: TransactionSplitRepositoryImpl
    private var groceries = 0L
    private var shopping = 0L

    @Before
    fun setUp() = runBlocking {
        db = inMemoryDb()
        transactions = TransactionRepositoryImpl(db, fixedClock())
        splits = TransactionSplitRepositoryImpl(db)
        groceries = db.categoryDao().getByKey("groceries")!!.id
        shopping = db.categoryDao().getByKey("shopping")!!.id
    }

    @After fun tearDown() { db.close() }

    private suspend fun purchase(amount: Long, merchant: String = "PYATEROCHKA 1234"): Long = transactions.addManual(
        ManualTransactionInput(occurredAt = T0, amountMinor = amount, currency = "RUB", direction = Direction.EXPENSE, merchant = merchant, categoryId = groceries),
    )

    @Test
    fun replace_storesParts_andTransactionCarriesThem() = runTest {
        val id = purchase(150_000)
        splits.replace(id, listOf(SplitPartInput(shopping, 30_000, "Губки"), SplitPartInput(groceries, 100_000, "  ")))

        val tx = transactions.observeById(id).first()!!
        assertThat(tx.splits.map { it.categoryId to it.amountMinor }).containsExactly(shopping to 30_000L, groceries to 100_000L).inOrder()
        assertThat(tx.splits.map { it.label }).containsExactly("Губки", null).inOrder()
        assertThat(tx.unsplitAmountMinor).isEqualTo(20_000L)
        assertThat(transactions.observe(TransactionFilter()).first().single().splits).hasSize(2)
        // Сама операция не меняется.
        assertThat(tx.amountMinor).isEqualTo(150_000L)
        assertThat(tx.categoryId).isEqualTo(groceries)
    }

    @Test
    fun replace_replacesPreviousSplit_andEmptyClears() = runTest {
        val id = purchase(10_000)
        splits.replace(id, listOf(SplitPartInput(shopping, 4_000)))
        splits.replace(id, listOf(SplitPartInput(groceries, 6_000), SplitPartInput(null, 4_000)))
        assertThat(splits.observe(id).first().map { it.amountMinor }).containsExactly(6_000L, 4_000L).inOrder()

        splits.replace(id, emptyList())
        assertThat(splits.observe(id).first()).isEmpty()
    }

    @Test
    fun replace_rejectsPartsAboveAmount_andNonPositive_keepingOldSplit() = runTest {
        val id = purchase(10_000)
        splits.replace(id, listOf(SplitPartInput(shopping, 10_000)))

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { splits.replace(id, listOf(SplitPartInput(shopping, 6_000), SplitPartInput(groceries, 4_001))) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { splits.replace(id, listOf(SplitPartInput(shopping, 0))) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { splits.replace(id, listOf(SplitPartInput(shopping, Long.MAX_VALUE), SplitPartInput(groceries, Long.MAX_VALUE))) }
        }
        assertThrows(NoSuchElementException::class.java) {
            runBlocking { splits.replace(9_999, listOf(SplitPartInput(shopping, 1))) }
        }
        assertThat(splits.observe(id).first().map { it.amountMinor }).containsExactly(10_000L)
    }

    @Test
    fun deletingOperation_deletesParts_andShrinkingAmountClearsSplit() = runTest {
        val a = purchase(10_000)
        splits.replace(a, listOf(SplitPartInput(shopping, 8_000)))
        val tx = transactions.observeById(a).first()!!
        // Сумма меньше разбиения — разбиение снимается.
        transactions.update(tx.copy(amountMinor = 7_000))
        assertThat(splits.observe(a).first()).isEmpty()

        val b = purchase(20_000)
        splits.replace(b, listOf(SplitPartInput(shopping, 5_000)))
        transactions.update(transactions.observeById(b).first()!!.copy(amountMinor = 6_000))
        assertThat(splits.observe(b).first()).hasSize(1)
        transactions.delete(b)
        assertThat(db.transactionSplitDao().getByParent(b)).isEmpty()
    }

    @Test
    fun search_findsByDisplayName_andStillByRawName() = runTest {
        purchase(1_000, merchant = "YANDEX*GO*TAXI MOSCOW")
        purchase(2_000, merchant = "PYATEROCHKA 1234")
        purchase(3_000, merchant = "Кофемания")
        MerchantAliasRepositoryImpl(db, fixedClock()).rename("YANDEX*GO*TAXI MOSCOW", "Яндекс Go")

        suspend fun found(query: String) = transactions.observe(TransactionFilter(query = query)).first().map { it.amountMinor }
        assertThat(found("яндекс")).containsExactly(1_000L)
        assertThat(found("taxi")).containsExactly(1_000L)
        assertThat(found("pyaterochka")).containsExactly(2_000L)
        assertThat(found("КОФЕ")).containsExactly(3_000L)
        assertThat(found("нет такого")).isEmpty()
        // Остальные условия фильтра работают и на пути поиска по имени.
        assertThat(transactions.observe(TransactionFilter(query = "яндекс", minAmountMinor = 1_500)).first()).isEmpty()
        assertThat(transactions.observe(TransactionFilter(query = "яндекс")).first().single().displayMerchant).isEqualTo("Яндекс Go")
    }
}
