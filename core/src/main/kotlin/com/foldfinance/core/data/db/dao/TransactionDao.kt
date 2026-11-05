package com.foldfinance.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Update
import androidx.sqlite.db.SupportSQLiteQuery
import com.foldfinance.core.data.db.entity.TransactionEntity
import com.foldfinance.core.model.Source
import com.foldfinance.parser.Direction
import com.foldfinance.parser.MessageChannel
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    /** @return id или -1, если операция с таким `dedup_key` уже есть. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(entity: TransactionEntity): Long

    @Update
    suspend fun update(entity: TransactionEntity)

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Long): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE id = :id")
    fun observeById(id: Long): Flow<TransactionEntity?>

    @Query("SELECT * FROM transactions WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE dedup_key = :dedupKey")
    suspend fun getByDedupKey(dedupKey: String): TransactionEntity?

    @RawQuery(observedEntities = [TransactionEntity::class])
    fun observeByQuery(query: SupportSQLiteQuery): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE needs_review = 1 ORDER BY occurred_at DESC, id DESC")
    fun observeReviewQueue(): Flow<List<TransactionEntity>>

    @Query("SELECT COUNT(*) FROM transactions WHERE needs_review = 1")
    fun observeReviewCount(): Flow<Int>

    @Query("SELECT * FROM transactions WHERE raw_message_id = :rawMessageId ORDER BY op_index")
    suspend fun getByRawMessage(rawMessageId: Long): List<TransactionEntity>

    /**
     * Кандидат кросс-источникового дедупа: та же сумма, валюта, маска карты (`IS` — null-безопасно)
     * и исходное направление, создан из другого канала, окно [from]..[to], и к нему ещё не привязано
     * сообщение из канала [channel]. Ближайший по времени.
     */
    @Query(
        """
        SELECT t.* FROM transactions t
        WHERE t.amount_minor = :amountMinor AND t.currency = :currency AND t.card_mask IS :cardMask
          AND t.parsed_direction = :parsedDirection
          AND t.source != 'MANUAL' AND t.source != :source
          AND t.occurred_at BETWEEN :from AND :to
          AND NOT EXISTS (
            SELECT 1 FROM raw_messages r WHERE r.transaction_id = t.id AND r.channel = :channel
          )
        ORDER BY ABS(t.occurred_at - :at) ASC, t.id ASC
        LIMIT 1
        """,
    )
    suspend fun findCrossSourceCandidate(
        amountMinor: Long,
        currency: String,
        cardMask: String?,
        parsedDirection: Direction,
        source: Source,
        channel: MessageChannel,
        from: Long,
        to: Long,
        at: Long,
    ): TransactionEntity?

    /**
     * Покупка для связывания с возвратом: расход той же валюты в окне, сумма не меньше возврата.
     * Мерчант и карта сравниваются, только если заданы. Сначала точное совпадение суммы, потом самая поздняя.
     */
    @Query(
        """
        SELECT * FROM transactions
        WHERE direction = 'EXPENSE' AND currency = :currency
          AND (:merchantNorm IS NULL OR merchant_norm = :merchantNorm)
          AND (:cardMask IS NULL OR card_mask = :cardMask)
          AND amount_minor >= :amountMinor
          AND occurred_at BETWEEN :from AND :to
        ORDER BY (amount_minor = :amountMinor) DESC, occurred_at DESC, id DESC
        LIMIT 1
        """,
    )
    suspend fun findRefundCandidate(
        currency: String,
        merchantNorm: String?,
        cardMask: String?,
        amountMinor: Long,
        from: Long,
        to: Long,
    ): TransactionEntity?

    @Query("UPDATE transactions SET category_id = :categoryId, user_edited = 1, updated_at = :now WHERE id IN (:ids)")
    suspend fun setCategory(ids: List<Long>, categoryId: Long?, now: Long): Int

    /** Подтверждение пользователя: из очереди проверки, и перепарсинг не вернёт обратно (см. `review_confirmed`). */
    @Query("UPDATE transactions SET needs_review = 0, review_confirmed = 1, updated_at = :now WHERE id IN (:ids)")
    suspend fun confirmReview(ids: List<Long>, now: Long): Int

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    /** Применить правило мерчанта к прошлым операциям, кроме правленых вручную. */
    @Query(
        """
        UPDATE transactions SET category_id = :categoryId, updated_at = :now
        WHERE user_edited = 0 AND merchant_norm LIKE :likePattern ESCAPE '\'
        """,
    )
    suspend fun applyMerchantCategory(likePattern: String, categoryId: Long, now: Long): Int

    /** Переводы, затрагивающие карту [mask] (как свою или как контрагента). */
    @Query(
        """
        SELECT * FROM transactions
        WHERE kind = 'TRANSFER' AND (card_mask = :mask OR counterparty_card_mask = :mask)
        """,
    )
    suspend fun getTransfersInvolving(mask: String): List<TransactionEntity>

    @Query("UPDATE transactions SET direction = :direction, updated_at = :now WHERE id = :id")
    suspend fun setDirection(id: Long, direction: Direction, now: Long)
}
