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
