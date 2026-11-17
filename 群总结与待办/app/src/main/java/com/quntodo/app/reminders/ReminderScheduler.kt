package com.quntodo.app.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.quntodo.app.data.db.entity.TodoEntity
import com.quntodo.app.util.RuntimeState
import java.time.Instant
import java.time.ZoneId

class ReminderScheduler(private val context: Context) {

    fun schedule(todo: TodoEntity) {
        cancel(todo.id)
        if (todo.completed || todo.deletedAt != null) return
        if (todo.reviewState != TodoEntity.STATE_CONFIRMED) return
        val at = todo.reminderAt ?: return
        if (at <= System.currentTimeMillis() + 1_000L) return

        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = alarmIntent(todo.id)
        runCatching {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            } else {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            }
        }.onFailure {
            RuntimeState.error("设置提醒失败：${it.message}")
        }
    }

    fun cancel(todoId: Long) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        runCatching { manager.cancel(alarmIntent(todoId)) }
    }

    fun rescheduleAll(todos: List<TodoEntity>) {
        todos.forEach { schedule(it) }
    }

    private fun alarmIntent(todoId: Long): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_REMIND
            putExtra(ReminderReceiver.EXTRA_TODO_ID, todoId)
        }
        return PendingIntent.getBroadcast(
            context,
            todoId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        fun computeReminderAt(
            deadlineAt: Long?,
            deadlineHasTime: Boolean,
            leadMinutes: Int,
            dateOnlyHour: Int,
            now: Long = System.currentTimeMillis()
        ): Long? {
            if (deadlineAt == null) return null
            if (deadlineAt <= now) return null

            if (deadlineHasTime) {
                val target = deadlineAt - leadMinutes * 60_000L
                return if (target > now) target else now + 60_000L
            }

            val zone = ZoneId.systemDefault()
            val date = Instant.ofEpochMilli(deadlineAt).atZone(zone).toLocalDate()
            val target = date.atTime(dateOnlyHour.coerceIn(0, 23), 0)
                .atZone(zone)
                .toInstant()
                .toEpochMilli()
            return if (target > now) target else now + 60_000L
        }
    }
}
