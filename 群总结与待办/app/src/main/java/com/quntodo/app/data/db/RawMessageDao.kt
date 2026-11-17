package com.quntodo.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.quntodo.app.data.db.entity.RawMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RawMessageDao {

    @Insert
    suspend fun insert(message: RawMessageEntity): Long

    @Insert
    suspend fun insertAll(messages: List<RawMessageEntity>): List<Long>

    @Query(
        "SELECT * FROM raw_messages WHERE consumedAt IS NULL AND ignored = 0 " +
            "ORDER BY postedAt ASC, id ASC"
    )
    fun observeUnconsumed(): Flow<List<RawMessageEntity>>

    @Query(
        "SELECT * FROM raw_messages WHERE consumedAt IS NULL AND ignored = 0 " +
            "ORDER BY postedAt ASC, id ASC"
    )
    suspend fun getUnconsumed(): List<RawMessageEntity>

    @Query("SELECT * FROM raw_messages WHERE id IN (:ids) ORDER BY postedAt ASC, id ASC")
    suspend fun getByIds(ids: List<Long>): List<RawMessageEntity>

    @Query("SELECT COUNT(*) FROM raw_messages WHERE consumedAt IS NULL AND ignored = 0")
    suspend fun countUnconsumed(): Int

    @Query(
        "SELECT COUNT(*) FROM raw_messages WHERE groupKey = :groupKey " +
            "AND contentHash = :hash AND postedAt BETWEEN :from AND :to"
    )
    suspend fun countSimilar(groupKey: String, hash: String, from: Long, to: Long): Int

    @Query(
        "SELECT * FROM raw_messages WHERE notificationKey = :key " +
            "ORDER BY postedAt DESC, id DESC LIMIT 1"
    )
    suspend fun latestByNotificationKey(key: String): RawMessageEntity?

    @Query("UPDATE raw_messages SET ignored = 1 WHERE id = :id")
    suspend fun markIgnored(id: Long)

    @Query("UPDATE raw_messages SET ignored = 1 WHERE id IN (:ids)")
    suspend fun markIgnoredAll(ids: List<Long>)

    @Query("UPDATE raw_messages SET consumedAt = :now, batchId = :batchId WHERE id IN (:ids)")
    suspend fun markConsumed(ids: List<Long>, batchId: String, now: Long)

    @Query("UPDATE raw_messages SET batchId = :batchId WHERE id IN (:ids)")
    suspend fun attachBatch(ids: List<Long>, batchId: String)

    @Query("DELETE FROM raw_messages WHERE consumedAt IS NOT NULL AND consumedAt < :before")
    suspend fun purgeOldConsumed(before: Long)

    @Query("SELECT * FROM raw_messages WHERE id IN (:ids)")
    suspend fun getByIdsUnordered(ids: List<Long>): List<RawMessageEntity>
}
