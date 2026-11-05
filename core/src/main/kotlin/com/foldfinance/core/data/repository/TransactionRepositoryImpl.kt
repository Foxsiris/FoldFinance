package com.foldfinance.core.data.repository

import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import com.foldfinance.core.data.db.FoldFinanceDatabase
import com.foldfinance.core.data.db.TextNorm
import com.foldfinance.core.data.db.entity.TransactionEntity
import com.foldfinance.core.data.db.aliasDisplayNames
import com.foldfinance.core.data.db.byParent
import com.foldfinance.core.data.db.toModel
import com.foldfinance.core.data.db.toModels
import com.foldfinance.core.domain.category.MerchantNormalizer
import com.foldfinance.core.di.DataClock
import com.foldfinance.core.model.RawMessage
import com.foldfinance.core.model.Source
import com.foldfinance.core.model.Transaction
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class TransactionRepositoryImpl @Inject constructor(
    private val db: FoldFinanceDatabase,
    @DataClock private val clock: Clock,
) : TransactionRepository {

    private val dao = db.transactionDao()
    private val rawDao = db.rawMessageDao()
    private val aliasDao = db.merchantAliasDao()
    private val splitDao = db.transactionSplitDao()

    /** Ключ мерчанта → отображаемое имя; меняется только при изменении таблицы алиасов. */
    private val displayNames: Flow<Map<String, String>> =
        aliasDao.observeAll().map { it.aliasDisplayNames() }.distinctUntilChanged()

    /** Части разбиения по id операции; меняется только при изменении таблицы частей. */
    private val splits = splitDao.observeAll().map { it.byParent() }.distinctUntilChanged()

    /**
     * Поиск ([TransactionFilter.query]) работает по сырому названию и заметке (`search_text`), а
     * также по отображаемому имени мерчанта: если имя какого-то алиаса содержит запрос, текстовое
     * условие проверяется в Kotlin (сырое название или имя), остальные условия фильтра — в SQL.
     */
    override fun observe(filter: TransactionFilter): Flow<List<Transaction>> {
        val needle = TextNorm.normalize(filter.query)
        return displayNames.flatMapLatest { names ->
            val keys = if (needle == null) emptySet() else matchingAliasKeys(names, needle)
            val rows = if (keys.isEmpty()) {
                dao.observeByQuery(buildQuery(filter))
            } else {
                val canonical = HashMap<String, String>()
                dao.observeByQuery(buildQuery(filter.copy(query = null))).map { list ->
                    list.filter { e ->
                        needle!! in e.searchText ||
                            e.merchant?.let { m -> canonical.getOrPut(m) { MerchantNormalizer.canonical(m) } in keys } == true
                    }
                }
            }
            combine(rows, splits) { list, parts -> list.toModels(names, parts) }
        }
    }

    override fun observeById(id: Long): Flow<Transaction?> =
        combine(dao.observeById(id), displayNames, splitDao.observeByParent(id)) { e, names, parts ->
            e?.let { listOf(it).toModels(names, mapOf(id to parts.map { p -> p.toModel() })).single() }
        }

    override fun observeReviewQueue(): Flow<List<Transaction>> =
        combine(dao.observeReviewQueue(), displayNames, splits) { list, names, parts -> list.toModels(names, parts) }

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
            // Новая сумма меньше разбиения по чеку — разбиение больше не сходится, снимаем его.
            val splitTotal = splitDao.getByParent(transaction.id).fold(0L) { acc, p -> Math.addExact(acc, p.amountMinor) }
            if (splitTotal > transaction.amountMinor) splitDao.deleteByParent(transaction.id)
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
                    // Пользователь сохранил операцию — категория больше не «предложение ИИ».
                    aiCategory = false,
                    updatedAt = clock.instant(),
                ),
            )
        }
    }

    override suspend fun confirmReview(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        db.withTransaction {
            val now = clock.millis()
            // Подтверждённое предложение модели запоминается для мерчанта: следующие его операции
            // получат эту категорию без очереди проверки.
            dao.getByIds(ids.toList()).filter { it.aiCategory && it.categoryId != null }.forEach { tx ->
                val key = MerchantNormalizer.canonical(tx.merchant)
                if (key.isNotEmpty()) aliasDao.confirmCategory(key, requireNotNull(tx.categoryId), now)
            }
            dao.confirmReview(ids.toList(), now)
        }
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
        /** Ключи алиасов, чьё отображаемое имя содержит [needle] (уже нормализованный [TextNorm]). */
        fun matchingAliasKeys(names: Map<String, String>, needle: String): Set<String> =
            names.filterValues { name -> TextNorm.normalize(name)?.contains(needle) == true }.keys

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
