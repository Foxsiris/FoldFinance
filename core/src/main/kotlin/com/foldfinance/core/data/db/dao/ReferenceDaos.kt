package com.foldfinance.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.foldfinance.core.data.db.entity.CardEntity
import com.foldfinance.core.data.db.entity.CategoryEntity
import com.foldfinance.core.data.db.entity.KnownBalanceEntity
import com.foldfinance.core.data.db.entity.MerchantRuleEntity
import com.foldfinance.core.data.db.entity.SubscriptionEntity
import com.foldfinance.core.model.SubscriptionStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY sort_order, id")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY sort_order, id")
    suspend fun getAll(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE id = :id")
    fun observeById(id: Long): Flow<CategoryEntity?>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: Long): CategoryEntity?

    @Query("SELECT * FROM categories WHERE `key` = :key")
    suspend fun getByKey(key: String): CategoryEntity?

    /** @return id или -1, если категория с таким ключом уже есть. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(entity: CategoryEntity): Long

    @Query("SELECT COALESCE(MAX(sort_order), -1) FROM categories")
    suspend fun maxSortOrder(): Int

    @Query("UPDATE categories SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String): Int

    @Query("UPDATE categories SET icon_key = :iconKey WHERE id = :id")
    suspend fun setIcon(id: Long, iconKey: String): Int

    @Query("UPDATE categories SET color_argb = :colorArgb WHERE id = :id")
    suspend fun setColor(id: Long, colorArgb: Int?): Int
}

@Dao
interface MerchantRuleDao {
    @Query("SELECT * FROM merchant_rules ORDER BY created_at, id")
    fun observeAll(): Flow<List<MerchantRuleEntity>>

    @Query("SELECT * FROM merchant_rules ORDER BY created_at, id")
    suspend fun getAll(): List<MerchantRuleEntity>

    @Query("SELECT * FROM merchant_rules WHERE merchant_pattern = :pattern")
    suspend fun getByPattern(pattern: String): MerchantRuleEntity?

    @Insert
    suspend fun insert(entity: MerchantRuleEntity): Long

    @Update
    suspend fun update(entity: MerchantRuleEntity)

    @Query("DELETE FROM merchant_rules WHERE id = :id")
    suspend fun deleteById(id: Long): Int
}

@Dao
interface CardDao {
    @Query("SELECT * FROM cards ORDER BY is_own DESC, mask")
    fun observeAll(): Flow<List<CardEntity>>

    @Query("SELECT mask FROM cards WHERE is_own = 1")
    suspend fun getOwnMasks(): List<String>

    @Query("SELECT * FROM cards WHERE mask = :mask")
    suspend fun get(mask: String): CardEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(entity: CardEntity): Long

    @Upsert
    suspend fun upsert(entity: CardEntity)
}

@Dao
interface KnownBalanceDao {
    /** Одна запись на сообщение: повторная обработка сообщения заменяет её. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReplace(entity: KnownBalanceEntity): Long

    @Query("DELETE FROM known_balances WHERE raw_message_id = :rawMessageId")
    suspend fun deleteForRawMessage(rawMessageId: Long)

    @Query(
        """
        SELECT * FROM known_balances kb
        WHERE kb.id = (
          SELECT k2.id FROM known_balances k2 WHERE k2.card_mask = kb.card_mask
          ORDER BY k2.as_of DESC, k2.id DESC LIMIT 1
        )
        ORDER BY kb.card_mask
        """,
    )
    fun observeLatestPerCard(): Flow<List<KnownBalanceEntity>>

    @Query("SELECT * FROM known_balances WHERE card_mask = :cardMask ORDER BY as_of DESC, id DESC LIMIT 1")
    fun observeLatest(cardMask: String): Flow<KnownBalanceEntity?>

    /** Все известные остатки карты, новые сверху (индекс `card_mask, as_of`). */
    @Query("SELECT * FROM known_balances WHERE card_mask = :cardMask ORDER BY as_of DESC, id DESC")
    fun observeHistory(cardMask: String): Flow<List<KnownBalanceEntity>>
}

@Dao
interface SubscriptionDao {
    @Query("SELECT * FROM subscriptions ORDER BY next_charge_at, id")
    fun observeAll(): Flow<List<SubscriptionEntity>>

    @Query("SELECT * FROM subscriptions WHERE status = :status ORDER BY next_charge_at, id")
    fun observeByStatus(status: SubscriptionStatus): Flow<List<SubscriptionEntity>>

    @Query("SELECT * FROM subscriptions WHERE id = :id")
    fun observeById(id: Long): Flow<SubscriptionEntity?>

    @Insert
    suspend fun insert(entity: SubscriptionEntity): Long

    @Update
    suspend fun update(entity: SubscriptionEntity): Int

    @Query("UPDATE subscriptions SET status = :status WHERE id = :id")
    suspend fun setStatus(id: Long, status: SubscriptionStatus): Int

    @Query("DELETE FROM subscriptions WHERE id = :id")
    suspend fun deleteById(id: Long): Int
}
