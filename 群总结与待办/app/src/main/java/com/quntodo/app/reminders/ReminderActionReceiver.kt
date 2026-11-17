package com.quntodo.app.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.quntodo.app.QunTodoApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val todoId = intent.getLongExtra(EXTRA_TODO_ID, -1L)
        if (todoId <= 0L) return
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = appContext as? QunTodoApp ?: return@launch
                when (intent.action) {
                    ACTION_COMPLETE -> app.container.repository.setCompleted(todoId, true)
                    ACTION_SNOOZE -> app.container.repository.snooze(todoId, 10)
                }
                NotificationHelper.cancel(appContext, todoId)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_COMPLETE = "com.quntodo.app.action.COMPLETE"
        const val ACTION_SNOOZE = "com.quntodo.app.action.SNOOZE"
        const val EXTRA_TODO_ID = "todo_id"
    }
}
