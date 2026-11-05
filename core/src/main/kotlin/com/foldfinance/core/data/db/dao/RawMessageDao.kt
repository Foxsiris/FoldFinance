package com.foldfinance.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.foldfinance.core.data.db.entity.RawMessageEntity
import com.foldfinance.parser.MessageChannel
import kotlinx.coroutines.flow.Flow

@Dao
interface RawMessageDao {

    /** @return id или -1, если сообщение с таким (channel, source_key) уже сохранено. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(entity: RawMessageEntity): Long

    @Update
    suspend fun update(entity: RawMessageEntity)

    @Query("SELECT * FROM raw_messages WHERE id = :id")
    suspend fun getById(id: Long): RawMessageEntity?

    @Query("SELECT * FROM raw_messages WHERE channel = :channel AND source_key = :sourceKey")
    suspend fun findBySource(channel: MessageChannel, sourceKey: String): RawMessageEntity?

    /**
     * То же сообщение под другим идентификатором источника (например, SMS `_id` поменялся
     * после восстановления телефона): совпадают канал, отправитель, время и хеш текста.
     */
    @Query(
        """
        SELECT * FROM raw_messages
        WHERE channel = :channel AND sender = :sender AND text_hash = :textHash AND received_at = :receivedAt
        LIMIT 1
        """,
    )
    suspend fun findSameContent(channel: MessageChannel, sender: String, textHash: String, receivedAt: Long): RawMessageEntity?

    /** Сообщения, разобранные старой версией парсера, порциями по возрастанию id. */
    @Query(
        """
        SELECT * FROM raw_messages
        WHERE parser_version < :currentVersion AND id > :afterId
        ORDER BY id ASC LIMIT :limit
        """,
    )
    suspend fun getStale(currentVersion: Int, afterId: Long, limit: Int): List<RawMessageEntity>

    @Query("SELECT COUNT(*) FROM raw_messages WHERE parser_version < :currentVersion")
    suspend fun countStale(currentVersion: Int): Int

    /**
     * Кандидаты для локальной модели: regex-парсер не уверен или счёл сообщение не операцией, модели
     * сообщение ещё не отдавали, пользователь его не скрыл/не удалял. Порциями по возрастанию id;
     * окончательный отбор — `AiFallbackPolicy` в Kotlin.
     */
    @Query(
        """
        SELECT * FROM raw_messages
        WHERE parse_status IN ('UNCERTAIN', 'NOT_FINANCIAL') AND ai_attempted_at IS NULL AND user_deleted = 0
          AND id > :afterId
        ORDER BY id ASC LIMIT :limit
        """,
    )
    suspend fun getAiCandidates(afterId: Long, limit: Int): List<RawMessageEntity>

    @Query(
        """
        SELECT COUNT(*) FROM raw_messages
        WHERE parse_status IN ('UNCERTAIN', 'NOT_FINANCIAL') AND ai_attempted_at IS NULL AND user_deleted = 0
        """,
    )
    suspend fun countAiCandidates(): Int

    /** Сообщение отдавали модели (или решили не отдавать) — больше не кандидат. */
    @Query("UPDATE raw_messages SET ai_attempted_at = :at WHERE id = :id")
    suspend fun markAiAttempted(id: Long, at: Long)

    /** Только версия парсера: разбор моделью сохраняется, если новая версия regex по-прежнему не справляется. */
    @Query("UPDATE raw_messages SET parser_version = :version WHERE id = :id")
    suspend fun setParserVersion(id: Long, version: Int)

    /**
     * Сбойное сообщение («ядовитое»: запись его разбора падает): статус FAILED и версия [version],
     * чтобы перепарсинг не спотыкался о него до следующей версии парсера.
     */
    @Query("UPDATE raw_messages SET parse_status = 'FAILED', parser_version = :version WHERE id = :id")
    suspend fun markFailed(id: Long, version: Int)

    /** Перевести сохранённое сообщение на новый ключ источника (ленивая миграция ключей SMS). */
    @Query("UPDATE raw_messages SET source_key = :sourceKey WHERE id = :id")
    suspend fun updateSourceKey(id: Long, sourceKey: String)

    /**
     * Нераспознанные сообщения для раздела «Не распознано»: похожи на операцию (UNCERTAIN) или
     * разбор упал (FAILED), операции нет, пользователь их не скрыл. Новые сверху.
     */
    @Query(
        """
        SELECT * FROM raw_messages
        WHERE parse_status IN ('UNCERTAIN', 'FAILED') AND transaction_id IS NULL AND user_deleted = 0
        ORDER BY received_at DESC, id DESC
        """,
    )
    fun observeUnrecognized(): Flow<List<RawMessageEntity>>

    /** Скрыть нераспознанное сообщение (и не создавать из него операции при перепарсинге). */
    @Query("UPDATE raw_messages SET user_deleted = 1 WHERE id = :id AND transaction_id IS NULL")
    suspend fun hide(id: Long)

    /** Привязать нераспознанное сообщение к операции, добавленной вручную по его тексту. */
    @Query("UPDATE raw_messages SET transaction_id = :transactionId WHERE id = :id AND transaction_id IS NULL")
    suspend fun attachToTransaction(id: Long, transactionId: Long): Int

    @Query("SELECT * FROM raw_messages WHERE transaction_id = :transactionId ORDER BY received_at, id")
    fun observeByTransaction(transactionId: Long): Flow<List<RawMessageEntity>>

    @Query("SELECT * FROM raw_messages WHERE transaction_id = :transactionId ORDER BY received_at, id")
    suspend fun getByTransaction(transactionId: Long): List<RawMessageEntity>

    @Query(
        """
        UPDATE raw_messages SET user_deleted = 1
        WHERE transaction_id = :transactionId
           OR id = (SELECT raw_message_id FROM transactions WHERE id = :transactionId)
        """,
    )
    suspend fun markUserDeletedFor(transactionId: Long)
}
