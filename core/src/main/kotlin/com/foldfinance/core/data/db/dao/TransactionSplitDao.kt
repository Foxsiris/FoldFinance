package com.foldfinance.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.foldfinance.core.data.db.entity.TransactionSplitEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionSplitDao {
    @Query("SELECT * FROM transaction_split ORDER BY parent_id, id")
    fun observeAll(): Flow<List<TransactionSplitEntity>>

    @Query("SELECT * FROM transaction_split WHERE parent_id = :parentId ORDER BY id")
    fun observeByParent(parentId: Long): Flow<List<TransactionSplitEntity>>

    @Query("SELECT * FROM transaction_split WHERE parent_id = :parentId ORDER BY id")
    suspend fun getByParent(parentId: Long): List<TransactionSplitEntity>

    @Insert
    suspend fun insertAll(parts: List<TransactionSplitEntity>)

    @Query("DELETE FROM transaction_split WHERE parent_id = :parentId")
    suspend fun deleteByParent(parentId: Long): Int
}
