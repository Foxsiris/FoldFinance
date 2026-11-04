package com.foldfinance.app.ui.analytics

import app.cash.turbine.ReceiveTurbine
import com.foldfinance.core.data.repository.CategoryRepository
import com.foldfinance.core.data.repository.ManualTransactionInput
import com.foldfinance.core.data.repository.SubscriptionRepository
import com.foldfinance.core.data.repository.TransactionFilter
import com.foldfinance.core.data.repository.TransactionRepository
import com.foldfinance.core.data.repository.TransactionSort
import com.foldfinance.core.model.Category
import com.foldfinance.core.model.RawMessage
import com.foldfinance.core.model.Subscription
import com.foldfinance.core.model.SubscriptionStatus
import com.foldfinance.core.model.Transaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** In-memory [TransactionRepository]: applies the period, direction and currency parts of the filter. */
class FakeTransactionRepository(initial: List<Transaction> = emptyList()) : TransactionRepository {
    val rows = MutableStateFlow(initial)

    override fun observe(filter: TransactionFilter): Flow<List<Transaction>> = rows.map { list ->
        list.filter { tx ->
            (filter.from == null || !tx.occurredAt.isBefore(filter.from)) &&
                (filter.to == null || tx.occurredAt.isBefore(filter.to)) &&
                (filter.directions == null || tx.direction in filter.directions!!) &&
                (filter.currency == null || tx.currency == filter.currency)
        }.let { filtered ->
            when (filter.sort) {
                TransactionSort.DATE_ASC -> filtered.sortedBy { it.occurredAt }
                else -> filtered.sortedByDescending { it.occurredAt }
            }
        }
    }

    override fun observeById(id: Long): Flow<Transaction?> = rows.map { l -> l.firstOrNull { it.id == id } }
    override fun observeReviewQueue(): Flow<List<Transaction>> = rows.map { l -> l.filter { it.needsReview } }
    override fun observeReviewCount(): Flow<Int> = rows.map { l -> l.count { it.needsReview } }
    override fun observeRawMessages(transactionId: Long): Flow<List<RawMessage>> = MutableStateFlow(emptyList())
    override suspend fun addManual(input: ManualTransactionInput): Long = throw UnsupportedOperationException()
    override suspend fun update(transaction: Transaction) = throw UnsupportedOperationException()
    override suspend fun confirmReview(ids: Collection<Long>) = throw UnsupportedOperationException()
    override suspend fun setCategory(ids: Collection<Long>, categoryId: Long?) = throw UnsupportedOperationException()
    override suspend fun delete(id: Long) = throw UnsupportedOperationException()
}

/** In-memory [CategoryRepository]. */
class FakeCategoryRepository(initial: List<Category> = InsightsSamples.categories) : CategoryRepository {
    val rows = MutableStateFlow(initial)

    override fun observeAll(): Flow<List<Category>> = rows.map { l -> l.sortedBy { it.sortOrder } }
    override fun observeById(id: Long): Flow<Category?> = rows.map { l -> l.firstOrNull { it.id == id } }
    override suspend fun getAll(): List<Category> = rows.value

    override suspend fun add(name: String, iconKey: String, colorArgb: Int?): Long {
        require(name.isNotBlank())
        val id = (rows.value.maxOfOrNull { it.id } ?: 0) + 1
        rows.update {
            it + Category(id, "user_$id", name.trim(), iconKey, colorArgb, isSystem = false, sortOrder = (it.maxOfOrNull { c -> c.sortOrder } ?: 0) + 1)
        }
        return id
    }

    override suspend fun rename(id: Long, name: String) = edit(id) { it.copy(name = name.trim()) }
    override suspend fun setIcon(id: Long, iconKey: String) = edit(id) { it.copy(iconKey = iconKey) }
    override suspend fun setColor(id: Long, colorArgb: Int?) = edit(id) { it.copy(colorArgb = colorArgb) }
    override suspend fun ensureDefaults() = Unit

    private fun edit(id: Long, change: (Category) -> Category) = rows.update { l -> l.map { if (it.id == id) change(it) else it } }
}

/** In-memory [SubscriptionRepository]. */
class FakeSubscriptionRepository(initial: List<Subscription> = emptyList()) : SubscriptionRepository {
    val rows = MutableStateFlow(initial)

    override fun observeAll(): Flow<List<Subscription>> = rows.map { l -> l.sortedWith(compareBy({ it.nextChargeAt }, { it.id })) }
    override fun observeByStatus(status: SubscriptionStatus): Flow<List<Subscription>> =
        observeAll().map { l -> l.filter { it.status == status } }
    override fun observeById(id: Long): Flow<Subscription?> = rows.map { l -> l.firstOrNull { it.id == id } }

    override suspend fun upsert(subscription: Subscription): Long {
        if (subscription.id == 0L) {
            val id = (rows.value.maxOfOrNull { it.id } ?: 0) + 1
            rows.update { it + subscription.copy(id = id) }
            return id
        }
        rows.update { l -> l.map { if (it.id == subscription.id) subscription else it } }
        return subscription.id
    }

    override suspend fun setStatus(id: Long, status: SubscriptionStatus) =
        rows.update { l -> l.map { if (it.id == id) it.copy(status = status) else it } }

    override suspend fun delete(id: Long) = rows.update { l -> l.filterNot { it.id == id } }
}

/** Skips items until one matches [predicate]. */
suspend fun <T> ReceiveTurbine<T>.awaitUntil(predicate: (T) -> Boolean): T {
    while (true) {
        val item = awaitItem()
        if (predicate(item)) return item
    }
}
