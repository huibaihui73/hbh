package com.quntodo.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.quntodo.app.data.db.entity.TodoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TodoDao {

    @Query(
        "SELECT * FROM todos WHERE deletedAt IS NULL AND reviewState = 'CONFIRMED' " +
            "AND completed = :completed ORDER BY pinned DESC, createdAt DESC, id DESC"
    )
    fun observeByCompletion(completed: Boolean): Flow<List<TodoEntity>>

    @Query(
        "SELECT * FROM todos WHERE deletedAt IS NULL AND reviewState = 'CONFIRMED' " +
            "AND completed = 0 ORDER BY deadlineAt IS NULL, deadlineAt ASC, createdAt DESC"
    )
    fun observeActive(): Flow<List<TodoEntity>>

    @Query("SELECT * FROM todos WHERE deletedAt IS NULL AND reviewState = 'PENDING' ORDER BY createdAt ASC")
    fun observePendingReview(): Flow<List<TodoEntity>>

    @Query("SELECT * FROM todos WHERE id = :id")
    suspend fun getById(id: Long): TodoEntity?

    @Query("SELECT * FROM todos WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<TodoEntity>

    @Query("SELECT * FROM todos WHERE deletedAt IS NULL AND reviewState = 'CONFIRMED' AND completed = 0")
    suspend fun getActiveOnce(): List<TodoEntity>

    @Query(
        "SELECT * FROM todos WHERE deletedAt IS NULL AND reviewState = 'CONFIRMED' " +
            "AND groupKey = :groupKey"
    )
    suspend fun getActiveByGroup(groupKey: String): List<TodoEntity>

    @Query("SELECT COUNT(*) FROM todos WHERE deletedAt IS NULL")
    suspend fun countActive(): Int

    @Query("SELECT COUNT(*) FROM todos WHERE deletedAt IS NULL AND reviewState = 'PENDING'")
    suspend fun countPending(): Int

    @Insert
    suspend fun insert(todo: TodoEntity): Long

    @Insert
    suspend fun insertAll(todos: List<TodoEntity>): List<Long>

    @Update
    suspend fun update(todo: TodoEntity)

    @Query("UPDATE todos SET pinned = :pinned, updatedAt = :now WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean, now: Long)

    @Query(
        "UPDATE todos SET completed = :completed, completedAt = :completedAt, updatedAt = :now " +
            "WHERE id = :id"
    )
    suspend fun setCompleted(id: Long, completed: Boolean, completedAt: Long?, now: Long)

    @Query("UPDATE todos SET reminderAt = :reminderAt, updatedAt = :now WHERE id = :id")
    suspend fun setReminderAt(id: Long, reminderAt: Long?, now: Long)

    @Query("UPDATE todos SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: Long, now: Long)

    @Query("UPDATE todos SET deletedAt = NULL, updatedAt = :now WHERE id = :id")
    suspend fun restore(id: Long, now: Long)

    @Query("DELETE FROM todos WHERE deletedAt IS NOT NULL AND deletedAt < :before")
    suspend fun purgeDeleted(before: Long)

    @Query(
        "UPDATE todos SET reviewState = 'CONFIRMED', updatedAt = :now " +
            "WHERE batchId = :batchId AND reviewState = 'PENDING'"
    )
    suspend fun confirmBatch(batchId: String, now: Long)

    @Query(
        "UPDATE todos SET reviewState = 'REJECTED', deletedAt = :now, updatedAt = :now " +
            "WHERE batchId = :batchId AND reviewState = 'PENDING'"
    )
    suspend fun rejectBatch(batchId: String, now: Long)
}
