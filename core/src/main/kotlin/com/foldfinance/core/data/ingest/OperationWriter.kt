package com.foldfinance.core.data.ingest

import com.foldfinance.core.data.db.FoldFinanceDatabase
import com.foldfinance.core.data.db.TextNorm
import com.foldfinance.core.data.db.entity.CardEntity
import com.foldfinance.core.data.db.entity.KnownBalanceEntity
import com.foldfinance.core.data.db.entity.RawMessageEntity
import com.foldfinance.core.data.db.entity.TransactionEntity
import com.foldfinance.core.data.db.toModel
import com.foldfinance.core.domain.category.Categorizer
import com.foldfinance.core.model.Category
import com.foldfinance.core.model.MerchantRule
import com.foldfinance.core.model.ParseStatus
import com.foldfinance.parser.Direction
import com.foldfinance.parser.PARSER_VERSION
import com.foldfinance.parser.ParseResult
import com.foldfinance.parser.ParsedBalance
import com.foldfinance.parser.ParsedOperation
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * Запись результата разбора одного [RawMessageEntity] в БД. Общая часть приёма и перепарсинга.
 * Все методы вызываются **внутри** транзакции БД.
 */
internal class OperationWriter(
    private val db: FoldFinanceDatabase,
    private val clock: Clock,
) {
    private val txDao = db.transactionDao()
    private val rawDao = db.rawMessageDao()
    private val cardDao = db.cardDao()

    data class Applied(
        val transactionIds: List<Long>,
        val created: Int,
        val merged: Int,
        val updated: Int,
        val needsReview: Boolean,
    )

    private class Context(
        val ownMasks: Set<String>,
        val rules: List<MerchantRule>,
        val categories: List<Category>,
    )

    /**
     * Применить [result] к сообщению [raw] (уже сохранённому, `raw.id != 0`).
     * Существующие операции этого сообщения обновляются по номеру операции, кроме `userEdited`.
     * Обновляет статус, версию парсера и ссылку на операцию у сообщения.
     */
    suspend fun apply(raw: RawMessageEntity, result: ParseResult): Applied {
        val ops: List<ParsedOperation>
        val uncertain: Boolean
        val balance: ParsedBalance?
        val parserId: String?
        val status: ParseStatus
        when (result) {
            is ParseResult.Parsed -> {
                ops = result.operations; uncertain = false; balance = result.balance
                parserId = result.parserId; status = ParseStatus.PARSED
            }
            is ParseResult.Uncertain -> {
                ops = listOfNotNull(result.partial); uncertain = true; balance = result.balance
                parserId = result.parserId; status = ParseStatus.UNCERTAIN
            }
            ParseResult.NotFinancial -> {
                ops = emptyList(); uncertain = false; balance = null
                parserId = null; status = ParseStatus.NOT_FINANCIAL
            }
        }

        val ctx = Context(
            ownMasks = cardDao.getOwnMasks().toSet(),
            rules = db.merchantRuleDao().getAll().map { it.toModel() },
            categories = db.categoryDao().getAll().map { it.toModel() },
        )
        val existingByIndex = txDao.getByRawMessage(raw.id).associateBy { it.opIndex }
        val ids = mutableListOf<Long>()
        var created = 0
        var merged = 0
        var updated = 0
        var review = false

        ops.forEachIndexed { index, op ->
            val existing = existingByIndex[index]
            when {
                existing != null && existing.userEdited -> {
                    ids += existing.id
                    review = review || existing.needsReview
                }
                existing != null -> {
                    val rebuilt = rebuild(existing, raw, op, uncertain, ctx)
                    if (rebuilt != existing) {
                        txDao.update(rebuilt)
                        updated++
                    }
                    ids += existing.id
                    review = review || rebuilt.needsReview
                }
                raw.userDeleted -> Unit
                // Основная операция сообщения уже слита с операцией из другого канала.
                index == 0 && raw.transactionId != null -> ids += raw.transactionId
                else -> {
                    registerCard(op.cardMask)
                    val (id, isNew) = createOrMerge(raw, index, op, uncertain, ctx)
                    ids += id
                    if (isNew) created++ else merged++
                    review = review || (isNew && needsReview(op, uncertain))
                }
            }
        }

        // Операции, которых новая версия парсера больше не видит: не удаляем молча, а отправляем на проверку.
        existingByIndex.values
            .filter { it.opIndex >= ops.size && !it.userEdited && !it.needsReview }
            .forEach { txDao.update(it.copy(needsReview = true, updatedAt = clock.instant())) }

        db.knownBalanceDao().deleteForRawMessage(raw.id)
        val balanceMask = balance?.cardMask ?: ops.mapNotNull { it.cardMask }.distinct().singleOrNull()
        if (balance != null && balanceMask != null) {
            registerCard(balanceMask)
            db.knownBalanceDao().insertReplace(
                KnownBalanceEntity(
                    cardMask = balanceMask,
                    amountMinor = balance.amountMinor,
                    currency = balance.currency,
                    asOf = raw.receivedAt,
                    rawMessageId = raw.id,
                ),
            )
        }

        rawDao.update(
            raw.copy(
                parseStatus = status,
                parserVersion = PARSER_VERSION,
                parserId = parserId,
                transactionId = raw.transactionId ?: ids.firstOrNull(),
            ),
        )
        return Applied(ids, created, merged, updated, review)
    }

    private suspend fun createOrMerge(
        raw: RawMessageEntity,
        index: Int,
        op: ParsedOperation,
        uncertain: Boolean,
        ctx: Context,
    ): Pair<Long, Boolean> {
        val dedupKey = dedupKey(raw, index)
        txDao.getByDedupKey(dedupKey)?.let { return it.id to false }

        val occurredAt = occurredAt(op, raw)
        val at = occurredAt.toEpochMilli()
        val candidate = txDao.findCrossSourceCandidate(
            amountMinor = op.amountMinor,
            currency = op.currency,
            cardMask = op.cardMask,
            parsedDirection = op.direction,
            source = raw.channel.toSource(),
            channel = raw.channel,
            from = at - CROSS_SOURCE_WINDOW.toMillis(),
            to = at + CROSS_SOURCE_WINDOW.toMillis(),
            at = at,
        )
        if (candidate != null) {
            // Дополняем то, чего не было в первом источнике; правки пользователя не трогаем.
            if (!candidate.userEdited && candidate.merchant == null && op.merchant != null) {
                txDao.update(
                    candidate.copy(
                        merchant = op.merchant,
                        merchantNorm = TextNorm.normalize(op.merchant),
                        searchText = TextNorm.searchText(op.merchant, candidate.note),
                        updatedAt = clock.instant(),
                    ),
                )
            }
            return candidate.id to false
        }

        val now = clock.instant()
        val fields = resolveFields(op, occurredAt, uncertain, ctx)
        val entity = TransactionEntity(
            occurredAt = occurredAt,
            amountMinor = op.amountMinor,
            currency = op.currency,
            direction = fields.direction,
            parsedDirection = op.direction,
            kind = op.kind,
            merchant = op.merchant,
            merchantNorm = TextNorm.normalize(op.merchant),
            cardMask = op.cardMask,
            counterpartyCardMask = op.counterpartyCardMask,
            categoryId = fields.categoryId,
            confidence = op.confidence,
            needsReview = fields.needsReview,
            source = raw.channel.toSource(),
            dedupKey = dedupKey,
            note = null,
            searchText = TextNorm.searchText(op.merchant, null),
            linkedTransactionId = fields.linkedTransactionId,
            userEdited = false,
            rawMessageId = raw.id,
            opIndex = index,
            createdAt = now,
            updatedAt = now,
        )
        val id = txDao.insertIgnore(entity)
        if (id == -1L) return requireNotNull(txDao.getByDedupKey(dedupKey)).id to false
        return id to true
    }

    private suspend fun rebuild(
        existing: TransactionEntity,
        raw: RawMessageEntity,
        op: ParsedOperation,
        uncertain: Boolean,
        ctx: Context,
    ): TransactionEntity {
        val occurredAt = occurredAt(op, raw)
        val fields = resolveFields(op, occurredAt, uncertain, ctx, excludeId = existing.id)
        val candidate = existing.copy(
            occurredAt = occurredAt,
            amountMinor = op.amountMinor,
            currency = op.currency,
            direction = fields.direction,
            parsedDirection = op.direction,
            kind = op.kind,
            merchant = op.merchant,
            merchantNorm = TextNorm.normalize(op.merchant),
            cardMask = op.cardMask,
            counterpartyCardMask = op.counterpartyCardMask,
            categoryId = fields.categoryId,
            confidence = op.confidence,
            needsReview = fields.needsReview,
            searchText = TextNorm.searchText(op.merchant, existing.note),
            linkedTransactionId = fields.linkedTransactionId,
        )
        return if (candidate == existing) existing else candidate.copy(updatedAt = clock.instant())
    }

    private class Fields(
        val direction: Direction,
        val categoryId: Long?,
        val needsReview: Boolean,
        val linkedTransactionId: Long?,
    )

    private suspend fun resolveFields(
        op: ParsedOperation,
        occurredAt: Instant,
        uncertain: Boolean,
        ctx: Context,
        excludeId: Long? = null,
    ): Fields {
        val direction = TransferClassifier.direction(op.kind, op.direction, op.counterpartyCardMask, ctx.ownMasks)
        val purchase = if (direction == Direction.REFUND) findPurchaseFor(op, occurredAt, excludeId) else null
        // Возврат уменьшает расходы своей категории — берём категорию исходной покупки.
        val categoryId = purchase?.categoryId ?: Categorizer.resolve(
            merchant = op.merchant,
            direction = direction,
            kind = op.kind,
            rules = ctx.rules,
            categories = ctx.categories,
        )
        return Fields(direction, categoryId, needsReview(op, uncertain), purchase?.id)
    }

    private suspend fun findPurchaseFor(op: ParsedOperation, occurredAt: Instant, excludeId: Long?): TransactionEntity? {
        val merchantNorm = TextNorm.normalize(op.merchant)
        if (merchantNorm == null && op.cardMask == null) return null
        val purchase = txDao.findRefundCandidate(
            currency = op.currency,
            merchantNorm = merchantNorm,
            cardMask = op.cardMask,
            amountMinor = op.amountMinor,
            from = occurredAt.minus(REFUND_LOOKBACK).toEpochMilli(),
            to = occurredAt.toEpochMilli(),
        )
        return purchase?.takeIf { it.id != excludeId }
    }

    private suspend fun registerCard(mask: String?) {
        if (mask != null) cardDao.insertIgnore(CardEntity(mask = mask, label = null, isOwn = false))
    }

    private fun needsReview(op: ParsedOperation, uncertain: Boolean): Boolean =
        uncertain || op.confidence < ParsedOperation.NEEDS_REVIEW_THRESHOLD

    private fun occurredAt(op: ParsedOperation, raw: RawMessageEntity): Instant =
        op.occurredAtMillis?.let(Instant::ofEpochMilli) ?: raw.receivedAt

    companion object {
        val CROSS_SOURCE_WINDOW: Duration = Duration.ofMinutes(3)
        val REFUND_LOOKBACK: Duration = Duration.ofDays(90)

        /** Детерминированный ключ операции: канал + идентичность источника + номер операции в сообщении. */
        fun dedupKey(raw: RawMessageEntity, index: Int): String = "${raw.channel.name}:${raw.sourceKey}#$index"
    }
}
