package com.quntodo.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.quntodo.app.data.db.entity.BatchEntity
import com.quntodo.app.data.db.entity.RawMessageEntity
import com.quntodo.app.data.db.entity.TodoEntity

@Database(
    entities = [TodoEntity::class, RawMessageEntity::class, BatchEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun todoDao(): TodoDao
    abstract fun rawMessageDao(): RawMessageDao
    abstract fun batchDao(): BatchDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "quntodo.db"
                ).build().also { instance = it }
            }
    }
}
