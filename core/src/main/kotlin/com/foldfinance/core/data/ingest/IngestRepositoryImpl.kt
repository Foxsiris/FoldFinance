package com.foldfinance.core.data.ingest

import androidx.room.withTransaction
import com.foldfinance.core.data.db.FoldFinanceDatabase
import com.foldfinance.core.data.db.entity.RawMessageEntity
import com.foldfinance.core.di.DataClock
import com.foldfinance.core.model.ParseStatus
import com.foldfinance.parser.MessageChannel
import com.foldfinance.parser.MessageMeta
import com.foldfinance.parser.PARSER_VERSION
import com.foldfinance.parser.ParseResult
import com.foldfinance.parser.ParserRegistry
import com.foldfinance.parser.UncertainReasons
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IngestRepositoryImpl @Inject constructor(
    private val db: FoldFinanceDatabase,
    private val registry: ParserRegistry,
    @DataClock clock: Clock,
) : IngestRepository {

    private val rawDao = db.rawMessageDao()
    private val writer = OperationWriter(db, clock)

    override suspend fun isKnown(channel: MessageChannel, sourceKey: String): Boolean =
        rawDao.findBySource(channel, sourceKey) != null

    override suspend fun ingest(input: IncomingMessage): IngestOutcome {
        // Быстрый путь для повторного сканирования: без разбора текста.
        rawDao.findBySource(input.channel, input.sourceKey)?.let { return IngestOutcome.AlreadyProcessed(it.id) }

        val textHash = SourceKeys.sha256Hex(input.text)
        val result = registry.parse(
            input.text,
            MessageMeta(
                channel = input.channel,
                sender = input.sender,
                receivedAtMillis = input.receivedAt.toEpochMilli(),
                title = input.title,
            ),
        )

        return db.withTransaction {
            findDuplicate(input, textHash)?.let { return@withTransaction IngestOutcome.AlreadyProcessed(it.id) }

            val raw = newRaw(input, textHash)
            val rawId = rawDao.insertIgnore(raw)
            if (rawId == -1L) {
                val existing = requireNotNull(rawDao.findBySource(input.channel, input.sourceKey))
                return@withTransaction IngestOutcome.AlreadyProcessed(existing.id)
            }

            val applied = writer.apply(raw.copy(id = rawId), result)
            when {
                result is ParseResult.NotFinancial -> IngestOutcome.NotFinancial(rawId)
                result is ParseResult.Uncertain && applied.transactionIds.isEmpty() ->
                    IngestOutcome.Unrecognized(rawId, result.reason)
                else -> IngestOutcome.Recorded(
                    rawMessageId = rawId,
                    transactionIds = applied.transactionIds,
                    createdCount = applied.created,
                    mergedCount = applied.merged,
                    needsReview = applied.needsReview,
                )
            }
        }
    }

    override suspend fun recordFailed(input: IncomingMessage): IngestOutcome {
        val textHash = SourceKeys.sha256Hex(input.text)
        return db.withTransaction {
            findDuplicate(input, textHash)?.let { return@withTransaction IngestOutcome.AlreadyProcessed(it.id) }
            val rawId = rawDao.insertIgnore(newRaw(input, textHash))
            if (rawId == -1L) {
                IngestOutcome.AlreadyProcessed(requireNotNull(rawDao.findBySource(input.channel, input.sourceKey)).id)
            } else {
                IngestOutcome.Unrecognized(rawId, UncertainReasons.PARSER_ERROR)
            }
        }
    }

    /**
     * Уже сохранённое сообщение: по ключу источника или по содержимому. SMS, найденная по
     * содержимому под другим ключом (старый формат ключа — только `_id`, или `_id` сменился после
     * восстановления телефона), переводится на новый ключ: следующая проверка [isKnown] найдёт её
     * без разбора текста. Вызывается внутри транзакции.
     */
    private suspend fun findDuplicate(input: IncomingMessage, textHash: String): RawMessageEntity? {
        rawDao.findBySource(input.channel, input.sourceKey)?.let { return it }
        val sameContent = rawDao.findSameContent(input.channel, input.sender, textHash, input.receivedAt.toEpochMilli())
            ?: return null
        if (input.channel == MessageChannel.SMS && sameContent.sourceKey != input.sourceKey) {
            rawDao.updateSourceKey(sameContent.id, input.sourceKey)
        }
        return sameContent
    }

    private fun newRaw(input: IncomingMessage, textHash: String) = RawMessageEntity(
        channel = input.channel,
        sender = input.sender,
        sourceKey = input.sourceKey,
        receivedAt = input.receivedAt,
        text = input.text,
        title = input.title,
        textHash = textHash,
        parseStatus = ParseStatus.FAILED,
        parserVersion = PARSER_VERSION,
        parserId = null,
        transactionId = null,
    )
}
