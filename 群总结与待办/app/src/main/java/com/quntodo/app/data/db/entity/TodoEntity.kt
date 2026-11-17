package com.quntodo.app.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "todos",
    indices = [
        Index("completed"),
        Index("pinned"),
        Index("deletedAt"),
        Index("reviewState"),
        Index("batchId"),
        Index("groupKey")
    ]
)
data class TodoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val body: String = "",
    val groupKey: String? = null,
    val groupName: String? = null,
    val deadlineAt: Long? = null,
    val deadlineHasTime: Boolean = false,
    val reminderAt: Long? = null,
    val pinned: Boolean = false,
    val completed: Boolean = false,
    val completedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val reviewState: String = STATE_CONFIRMED,
    val source: String = SOURCE_MANUAL,
    val batchId: String? = null,
    val sourceRawIds: String = "",
    val imagePaths: String = "",
    val confidence: Double? = null,
    val deletedAt: Long? = null
) {
    val sourceIdList: List<Long>
        get() = sourceRawIds.split(',').mapNotNull { it.trim().toLongOrNull() }

    val imagePathList: List<String>
        get() = imagePaths.split('\n').filter { it.isNotBlank() }

    companion object {
        const val STATE_PENDING = "PENDING"
        const val STATE_CONFIRMED = "CONFIRMED"
        const val STATE_REJECTED = "REJECTED"

        const val SOURCE_MANUAL = "MANUAL"
        const val SOURCE_RULE = "RULE"
        const val SOURCE_AI = "AI"
        const val SOURCE_SHARE = "SHARE"
    }
}
