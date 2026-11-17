package com.quntodo.app.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.quntodo.app.MainActivity
import com.quntodo.app.R
import com.quntodo.app.data.db.entity.TodoEntity
import com.quntodo.app.util.TimeFormat

object NotificationHelper {

    const val CHANNEL_REMINDERS = "todo_reminders"
    const val CHANNEL_STATUS = "app_status"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val reminders = NotificationChannel(
            CHANNEL_REMINDERS,
            "待办提醒",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "到截止时间前提醒你处理待办"
            enableVibration(true)
        }
        val status = NotificationChannel(
            CHANNEL_STATUS,
            "状态提示",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "抓取状态等低优先级提示"
        }
        manager.createNotificationChannels(listOf(reminders, status))
    }

    fun postReminder(context: Context, todo: TodoEntity) {
        ensureChannels(context)
        if (!canPost(context)) return

        val openIntent = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_TODO_ID, todo.id)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(
            context,
            todo.id.toInt(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val completePending = PendingIntent.getBroadcast(
            context,
            todo.id.toInt() + 100_000,
            Intent(context, ReminderActionReceiver::class.java).apply {
                action = ReminderActionReceiver.ACTION_COMPLETE
                putExtra(ReminderActionReceiver.EXTRA_TODO_ID, todo.id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozePending = PendingIntent.getBroadcast(
            context,
            todo.id.toInt() + 200_000,
            Intent(context, ReminderActionReceiver::class.java).apply {
                action = ReminderActionReceiver.ACTION_SNOOZE
                putExtra(ReminderActionReceiver.EXTRA_TODO_ID, todo.id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val deadlineLine = todo.deadlineAt?.let {
            "截止：" + TimeFormat.deadline(it, todo.deadlineHasTime)
        }
        val shortText = todo.body.ifBlank { deadlineLine ?: "该处理这件事了" }.take(60)
        val bigText = listOfNotNull(todo.body.ifBlank { null }, deadlineLine)
            .joinToString("\n")
            .ifBlank { shortText }

        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(todo.title)
            .setContentText(shortText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openPending)
            .addAction(0, "完成", completePending)
            .addAction(0, "稍后 10 分钟", snoozePending)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(todo.id.toInt(), notification)
        }
    }

    fun cancel(context: Context, todoId: Long) {
        runCatching { NotificationManagerCompat.from(context).cancel(todoId.toInt()) }
    }

    private fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }
}
