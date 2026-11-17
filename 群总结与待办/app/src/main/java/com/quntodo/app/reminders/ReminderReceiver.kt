package com.quntodo.app.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.quntodo.app.QunTodoApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val todoId = intent.getLongExtra(EXTRA_TODO_ID, -1L)
        if (todoId <= 0L) return
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = appContext as? QunTodoApp ?: return@launch
                val todo = app.container.database.todoDao().getById(todoId)
                if (todo != null &&
                    !todo.completed &&
                    todo.deletedAt == null &&
                    todo.reviewState == "CONFIRMED"
                ) {
                    NotificationHelper.postReminder(appContext, todo)
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_REMIND = "com.quntodo.app.action.REMIND"
        const val EXTRA_TODO_ID = "todo_id"
    }
}
