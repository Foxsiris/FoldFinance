package com.foldfinance.app.ui.fakes

import com.foldfinance.core.data.repository.AddRuleResult
import com.foldfinance.core.data.repository.BalanceRepository
import com.foldfinance.core.data.repository.CategoryRepository
import com.foldfinance.core.data.repository.ManualTransactionInput
import com.foldfinance.core.data.repository.MerchantRuleRepository
import com.foldfinance.core.data.repository.TransactionFilter
import com.foldfinance.core.data.repository.TransactionRepository
import com.foldfinance.core.data.repository.TransactionSort
import com.foldfinance.core.model.Category
import com.foldfinance.core.model.KnownBalance
import com.foldfinance.core.model.MerchantRule
import com.foldfinance.core.model.RawMessage
import com.foldfinance.core.model.Source
import com.foldfinance.core.model.Transaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.Instant

/**
 * In-memory [TransactionRepository] with the same filter semantics as the Room one, for
 * ViewModel, continuity and screenshot tests. Test-only: never shipped.
 */
class FakeTransactionRepository(
    initial: List<Transaction> = emptyList(),
    raw: List<RawMessage> = emptyList(),
    private val now: () -> Instant = { Instant.parse("2026-11-05T11:05:00Z") },
) : TransactionRepository {
    val items = MutableStateFlow(initial)
    val rawMessages = MutableStateFlow(raw)
    val added = mutableListOf<ManualTransactionInput>()
    val updated = mutableListOf<Transaction>()
    val deleted = mutableListOf<Long>()

    override fun observe(filter: TransactionFilter): Flow<List<Transaction>> = items.map { list -> apply(list, filter) }

    override fun observeById(id: Long): Flow<Transaction?> = items.map { list -> list.firstOrNull { it.id == id } }

    override fun observeReviewQueue(): Flow<List<Transaction>> =
        items.map { list -> list.filter { it.needsReview }.sortedByDescending { it.occurredAt } }

    override fun observeReviewCount(): Flow<Int> = items.map { list -> list.count { it.needsReview } }

    override fun observeRawMessages(transactionId: Long): Flow<List<RawMessage>> =
        rawMessages.map { list -> list.filter { it.transactionId == transactionId } }

    override suspend fun addManual(input: ManualTransactionInput): Long {
        added += input
        val id = (items.value.maxOfOrNull { it.id } ?: 0L) + 1
        val at = now()
        items.update {
            it + Transaction(
                id = id,
                occurredAt = input.occurredAt,
                amountMinor = input.amountMinor,
                currency = input.currency,
                direction = input.direction,
                kind = input.kind,
                merchant = input.merchant,
                cardMask = input.cardMask,
                categoryId = input.categoryId,
                confidence = 1f,
                needsReview = false,
                source = Source.MANUAL,
                dedupKey = "manual-$id",
                note = input.note,
                createdAt = at,
                updatedAt = at,
            )
        }
        return id
    }

    override suspend fun update(transaction: Transaction) {
        updated += transaction
        items.update { list -> list.map { if (it.id == transaction.id) transaction.copy(userEdited = true) else it } }
    }

    override suspend fun confirmReview(ids: Collection<Long>) {
        items.update { list -> list.map { if (it.id in ids) it.copy(needsReview = false) else it } }
    }

    override suspend fun setCategory(ids: Collection<Long>, categoryId: Long?) {
        items.update { list -> list.map { if (it.id in ids) it.copy(categoryId = categoryId, userEdited = true) else it } }
    }

    override suspend fun delete(id: Long) {
        deleted += id
        items.update { list -> list.filterNot { it.id == id } }
    }

    private fun apply(list: List<Transaction>, f: TransactionFilter): List<Transaction> {
        val filtered = list.filter { tx ->
            (f.from == null || !tx.occurredAt.isBefore(f.from)) &&
                (f.to == null || tx.occurredAt.isBefore(f.to)) &&
                (f.categoryIds == null || tx.categoryId in f.categoryIds!! || (f.includeUncategorized && tx.categoryId == null)) &&
                (f.directions == null || tx.direction in f.directions!!) &&
                (f.currency == null || tx.currency == f.currency) &&
                (f.minAmountMinor == null || tx.amountMinor >= f.minAmountMinor!!) &&
                (f.maxAmountMinor == null || tx.amountMinor <= f.maxAmountMinor!!) &&
                (f.query == null || listOfNotNull(tx.merchant, tx.note).any { it.contains(f.query!!, ignoreCase = true) })
        }
        return when (f.sort) {
            TransactionSort.DATE_DESC -> filtered.sortedWith(compareByDescending<Transaction> { it.occurredAt }.thenByDescending { it.id })
            TransactionSort.DATE_ASC -> filtered.sortedWith(compareBy<Transaction> { it.occurredAt }.thenBy { it.id })
            TransactionSort.AMOUNT_DESC -> filtered.sortedWith(compareByDescending<Transaction> { it.amountMinor }.thenByDescending { it.id })
            TransactionSort.AMOUNT_ASC -> filtered.sortedWith(compareBy<Transaction> { it.amountMinor }.thenBy { it.id })
        }
    }
}

class FakeCategoryRepository(initial: List<Category> = SampleData.categories) : CategoryRepository {
    val items = MutableStateFlow(initial)
    override fun observeAll(): Flow<List<Category>> = items
    override fun observeById(id: Long): Flow<Category?> = items.map { l -> l.firstOrNull { it.id == id } }
    override suspend fun getAll(): List<Category> = items.value
    override suspend fun add(name: String, iconKey: String, colorArgb: Int?): Long {
        val id = (items.value.maxOfOrNull { it.id } ?: 0L) + 1
        items.update { it + Category(id, "user_$id", name, iconKey, colorArgb, false, it.size) }
        return id
    }
    override suspend fun rename(id: Long, name: String) = items.update { l -> l.map { if (it.id == id) it.copy(name = name) else it } }
    override suspend fun setIcon(id: Long, iconKey: String) = items.update { l -> l.map { if (it.id == id) it.copy(iconKey = iconKey) else it } }
    override suspend fun setColor(id: Long, colorArgb: Int?) = items.update { l -> l.map { if (it.id == id) it.copy(colorArgb = colorArgb) else it } }
    override suspend fun ensureDefaults() = Unit
}

class FakeMerchantRuleRepository : MerchantRuleRepository {
    val rules = MutableStateFlow<List<MerchantRule>>(emptyList())
    val calls = mutableListOf<Triple<String, Long, Boolean>>()
    override fun observeAll(): Flow<List<MerchantRule>> = rules
    override suspend fun getAll(): List<MerchantRule> = rules.value
    override suspend fun add(merchantPattern: String, categoryId: Long, applyToExisting: Boolean): AddRuleResult {
        calls += Triple(merchantPattern, categoryId, applyToExisting)
        val id = rules.value.size + 1L
        rules.update { it + MerchantRule(id, merchantPattern, categoryId, Instant.EPOCH) }
        return AddRuleResult(id, 0)
    }
    override suspend fun delete(id: Long) = rules.update { l -> l.filterNot { it.id == id } }
}

class FakeBalanceRepository(initial: List<KnownBalance> = emptyList()) : BalanceRepository {
    val items = MutableStateFlow(initial)
    override fun observeLatest(): Flow<List<KnownBalance>> = items
    override fun observeLatest(cardMask: String): Flow<KnownBalance?> = items.map { l -> l.firstOrNull { it.cardMask == cardMask } }
}
