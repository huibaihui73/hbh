package com.quntodo.app

import android.app.Application
import com.quntodo.app.di.AppContainer
import com.quntodo.app.reminders.NotificationHelper
import kotlinx.coroutines.launch

class QunTodoApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        NotificationHelper.ensureChannels(this)
        container.scope.launch {
            runCatching { container.repository.onAppStart() }
        }
    }
}
