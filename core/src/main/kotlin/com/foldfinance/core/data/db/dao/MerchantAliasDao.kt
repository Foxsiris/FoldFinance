package com.foldfinance.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.foldfinance.core.data.db.entity.MerchantAliasEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MerchantAliasDao {
    @Query("SELECT * FROM merchant_alias ORDER BY merchant_key")
    fun observeAll(): Flow<List<MerchantAliasEntity>>

    @Query("SELECT * FROM merchant_alias ORDER BY merchant_key")
    suspend fun getAll(): List<MerchantAliasEntity>

    @Query("SELECT * FROM merchant_alias WHERE merchant_key = :key")
    suspend fun get(key: String): MerchantAliasEntity?

    @Query("SELECT * FROM merchant_alias WHERE merchant_key = :key")
    fun observe(key: String): Flow<MerchantAliasEntity?>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(entity: MerchantAliasEntity): Long

    @Upsert
    suspend fun upsert(entity: MerchantAliasEntity)

    /** Ключи, по которым модель уже отвечала (успешно или нет). */
    @Query("SELECT merchant_key FROM merchant_alias WHERE ai_checked_at IS NOT NULL")
    suspend fun getCheckedKeys(): List<String>

    /** Пользователь подтвердил предложенную моделью категорию мерчанта. */
    @Query(
        """
        UPDATE merchant_alias SET category_confirmed = 1, updated_at = :now
        WHERE merchant_key = :key AND ai_category_id = :categoryId
        """,
    )
    suspend fun confirmCategory(key: String, categoryId: Long, now: Long): Int
}
