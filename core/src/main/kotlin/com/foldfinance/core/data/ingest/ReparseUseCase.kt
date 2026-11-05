package com.foldfinance.core.data.ingest

import androidx.room.withTransaction
import com.foldfinance.core.data.db.FoldFinanceDatabase
import com.foldfinance.core.di.DataClock
import com.foldfinance.parser.MessageMeta
import com.foldfinance.parser.PARSER_VERSION
import com.foldfinance.parser.ParserRegistry
import kotlinx.coroutines.CancellationException
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/** Итог перепарсинга. */
data class ReparseReport(
    val messages: Int,
    val created: Int,
    val updated: Int,
    val merged: Int,
    /** Сообщения, на которых запись упала и которые помечены FAILED (только при `skipFailing`). */
    val failed: Int = 0,
)

/**
 * Перепарсинг сохранённых сообщений, разобранных версией парсера ниже [PARSER_VERSION]
 * (запускается фоновой задачей после обновления приложения).
 *
 * - Операции с `userEdited = true` не перезаписываются.
 * - Операции, удалённые пользователем, не воскрешаются.
 * - Операции, которых новый парсер больше не находит, не удаляются, а уходят на проверку.
 * - У сообщения обновляются статус и `parserVersion`. Каждое сообщение — отдельная транзакция БД,
 *   поэтому прерванный перепарсинг продолжается с места остановки.
 * - «Ядовитое» сообщение (запись его разбора бросает исключение) без `skipFailing` прерывает проход —
 *   ошибка могла быть временной, задача повторится. С `skipFailing` (последняя попытка задачи) оно
 *   помечается FAILED с текущей версией парсера и проход идёт дальше: одно сообщение не блокирует
 *   перепарсинг остальных навсегда.
 */
@Singleton
class ReparseUseCase @Inject constructor(
    private val db: FoldFinanceDatabase,
    private val registry: ParserRegistry,
    @DataClock clock: Clock,
) {
    private val rawDao = db.rawMessageDao()
    private val writer = OperationWriter(db, clock)

    /** Сколько сообщений ждут перепарсинга. */
    suspend fun pendingCount(): Int = rawDao.countStale(PARSER_VERSION)

    suspend operator fun invoke(batchSize: Int = DEFAULT_BATCH, skipFailing: Boolean = false): ReparseReport {
        require(batchSize > 0)
        var messages = 0
        var created = 0
        var updated = 0
        var merged = 0
        var failed = 0
        var afterId = 0L
        while (true) {
            val batch = rawDao.getStale(PARSER_VERSION, afterId, batchSize)
            if (batch.isEmpty()) break
            for (stale in batch) {
                afterId = stale.id
                val result = registry.parse(
                    stale.text,
                    MessageMeta(
                        channel = stale.channel,
                        sender = stale.sender,
                        receivedAtMillis = stale.receivedAt.toEpochMilli(),
                        title = stale.title,
                    ),
                )
                val applied = try {
                    db.withTransaction {
                        val raw = rawDao.getById(stale.id)
                        if (raw == null || raw.parserVersion >= PARSER_VERSION) null else writer.apply(raw, result)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (!skipFailing) throw e
                    rawDao.markFailed(stale.id, PARSER_VERSION)
                    failed++
                    continue
                } ?: continue
                messages++
                created += applied.created
                updated += applied.updated
                merged += applied.merged
            }
        }
        return ReparseReport(messages, created, updated, merged, failed)
    }

    private companion object {
        const val DEFAULT_BATCH = 200
    }
}
