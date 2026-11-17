package com.quntodo.app.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "batches")
data class BatchEntity(
    @PrimaryKey val batchId: String,
    val groupKey: String = "",
    val groupName: String = "",
    val summary: String = "",
    val messageCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val status: String = STATUS_PENDING,
    val aiUsed: Boolean = false,
    val aiError: String? = null
) {
    companion object {
        const val STATUS_PENDING = "PENDING"
        const val STATUS_CONFIRMED = "CONFIRMED"
        const val STATUS_IGNORED = "IGNORED"
    }
}
