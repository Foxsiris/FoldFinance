package com.foldfinance.core.data.repository

import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import com.foldfinance.core.data.db.FoldFinanceDatabase
import com.foldfinance.core.data.db.TextNorm
import com.foldfinance.core.data.db.entity.TransactionEntity
import com.foldfinance.core.data.db.toModel
import com.foldfinance.core.di.DataClock
import com.foldfinance.core.model.RawMessage
import com.foldfinance.core.model.Source
import com.foldfinance.core.model.Transaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionRepositoryImpl @Inject constructor(
    private val db: FoldFinanceDatabase,
    @DataClock private val clock: Clock,
) : TransactionRepository {

    private val dao = db.transactionDao()
    private val rawDao = db.rawMessageDao()

    override fun observe(filter: TransactionFilter): Flow<List<Transaction>> =
        dao.observeByQuery(buildQuery(filter)).map { list -> list.map { it.toModel() } }

    override fun observeById(id: Long): Flow<Transaction?> = dao.observeById(id).map { it?.toModel() }

    override fun observeReviewQueue(): Flow<List<Transaction>> =
        dao.observeReviewQueue().map { list -> list.map { it.toModel() } }

    override fun observeReviewCount(): Flow<Int> = dao.observeReviewCount()

    override fun observeRawMessages(transactionId: Long): Flow<List<RawMessage>> =
        rawDao.observeByTransaction(transactionId).map { list -> list.map { it.toModel() } }

    override suspend fun addManual(input: ManualTransactionInput): Long {
        val now = clock.instant()
        val entity = TransactionEntity(
            occurredAt = input.occurredAt,
            amountMinor = input.amountMinor,
            currency = input.currency,
            direction = input.direction,
            parsedDirection = input.direction,
            kind = input.kind,
            merchant = input.merchant,
            merchantNorm = TextNorm.normalize(input.merchant),
            cardMask = input.cardMask,
            counterpartyCardMask = null,
            categoryId = input.categoryId,
            confidence = 1f,
            needsReview = false,
            source = Source.MANUAL,
            dedupKey = "manual:${UUID.randomUUID()}",
            note = input.note,
            searchText = TextNorm.searchText(input.merchant, input.note),
            linkedTransactionId = null,
            userEdited = true,
            rawMessageId = null,
            opIndex = 0,
            createdAt = now,
            updatedAt = now,
        )
        return dao.insertIgnore(entity)
    }

    override suspend fun update(transaction: Transaction) {
        require(transaction.amountMinor > 0) { "amountMinor must be positive" }
        require(transaction.currency.length == 3) { "currency must be ISO 4217" }
        db.withTransaction {
            val current = dao.getById(transaction.id)
                ?: throw NoSuchElementException("transaction ${transaction.id} not found")
            dao.update(
                current.copy(
                    occurredAt = transaction.occurredAt,
                    amountMinor = transaction.amountMinor,
                    currency = transaction.currency,
                    direction = transaction.direction,
                    kind = transaction.kind,
                    merchant = transaction.merchant,
                    merchantNorm = TextNorm.normalize(transaction.merchant),
                    cardMask = transaction.cardMask,
                    categoryId = transaction.categoryId,
                    needsReview = transaction.needsReview,
                    note = transaction.note,
                    searchText = TextNorm.searchText(transaction.merchant, transaction.note),
                    linkedTransactionId = transaction.linkedTransactionId,
                    userEdited = true,
                    updatedAt = clock.instant(),
                ),
            )
        }
    }

    override suspend fun confirmReview(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        dao.confirmReview(ids.toList(), clock.millis())
    }

    override suspend fun setCategory(ids: Collection<Long>, categoryId: Long?) {
        if (ids.isEmpty()) return
        dao.setCategory(ids.toList(), categoryId, clock.millis())
    }

    override suspend fun delete(id: Long) {
        db.withTransaction {
            rawDao.markUserDeletedFor(id)
            dao.deleteById(id)
        }
    }

    internal companion object {
        /** Динамический SELECT по фильтру; все значения — через параметры, без конкатенации ввода. */
        fun buildQuery(filter: TransactionFilter): SimpleSQLiteQuery {
            val where = mutableListOf<String>()
            val args = mutableListOf<Any?>()

            filter.from?.let { where += "occurred_at >= ?"; args += it.toEpochMilli() }
            filter.to?.let { where += "occurred_at < ?"; args += it.toEpochMilli() }
            filter.categoryIds?.let { ids ->
                val parts = mutableListOf<String>()
                if (ids.isNotEmpty()) {
                    parts += "category_id IN (${ids.joinToString(",") { "?" }})"
                    args.addAll(ids)
                }
                if (filter.includeUncategorized) parts += "category_id IS NULL"
                where += if (parts.isEmpty()) "0" else parts.joinToString(" OR ", "(", ")")
            }
            filter.directions?.let { dirs ->
                where += if (dirs.isEmpty()) "0" else "direction IN (${dirs.joinToString(",") { "?" }})"
                args.addAll(dirs.map { it.name })
            }
            filter.currency?.let { where += "currency = ?"; args += it }
            filter.minAmountMinor?.let { where += "amount_minor >= ?"; args += it }
            filter.maxAmountMinor?.let { where += "amount_minor <= ?"; args += it }
            TextNorm.normalize(filter.query)?.let {
                where += "search_text LIKE ? ESCAPE '\\'"
                args += TextNorm.containsLike(it)
            }

            val order = when (filter.sort) {
                TransactionSort.DATE_DESC -> "occurred_at DESC, id DESC"
                TransactionSort.DATE_ASC -> "occurred_at ASC, id ASC"
                TransactionSort.AMOUNT_DESC -> "amount_minor DESC, id DESC"
                TransactionSort.AMOUNT_ASC -> "amount_minor ASC, id DESC"
            }
            val sql = buildString {
                append("SELECT * FROM transactions")
                if (where.isNotEmpty()) append(" WHERE ").append(where.joinToString(" AND "))
                append(" ORDER BY ").append(order)
            }
            return SimpleSQLiteQuery(sql, args.toTypedArray())
        }
    }
}
