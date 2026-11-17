package com.quntodo.app.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.quntodo.app.QunTodoApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RescheduleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = appContext as? QunTodoApp ?: return@launch
                app.container.repository.rescheduleAllReminders()
            } finally {
                pending.finish()
            }
        }
    }
}
