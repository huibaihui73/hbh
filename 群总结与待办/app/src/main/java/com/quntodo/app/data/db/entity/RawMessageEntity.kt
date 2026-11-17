package com.quntodo.app.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "raw_messages",
    indices = [
        Index(value = ["groupKey", "contentHash", "postedAt"]),
        Index("batchId"),
        Index("consumedAt"),
        Index("notificationKey")
    ]
)
data class RawMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val packageName: String = "",
    val groupKey: String,
    val groupName: String,
    val sender: String = "",
    val content: String = "",
    val postedAt: Long,
    val capturedAt: Long = System.currentTimeMillis(),
    val source: String = SOURCE_NOTIFICATION,
    val notificationKey: String = "",
    val contentHash: String = "",
    val batchId: String? = null,
    val consumedAt: Long? = null,
    val ignored: Boolean = false,
    val imagePath: String? = null
) {
    companion object {
        const val SOURCE_NOTIFICATION = "NOTIFICATION"
        const val SOURCE_SHARE = "SHARE"
        const val SOURCE_PASTE = "PASTE"
        const val SOURCE_IMAGE = "IMAGE"
        const val SOURCE_MANUAL = "MANUAL"
    }
}
