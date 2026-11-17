package com.quntodo.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.quntodo.app.data.db.entity.BatchEntity

@Dao
interface BatchDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(batch: BatchEntity)

    @Query("SELECT * FROM batches WHERE batchId = :batchId")
    suspend fun get(batchId: String): BatchEntity?

    @Query("UPDATE batches SET status = :status WHERE batchId = :batchId")
    suspend fun setStatus(batchId: String, status: String)

    @Query("SELECT * FROM batches ORDER BY createdAt DESC LIMIT 20")
    suspend fun recent(): List<BatchEntity>

    @Query("DELETE FROM batches WHERE createdAt < :before AND status != 'PENDING'")
    suspend fun purgeOld(before: Long)
}
