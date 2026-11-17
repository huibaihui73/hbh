package com.quntodo.app.di

import android.content.Context
import com.quntodo.app.ai.AiClient
import com.quntodo.app.data.db.AppDatabase
import com.quntodo.app.data.media.ImageStore
import com.quntodo.app.data.ocr.ScreenshotOcr
import com.quntodo.app.data.prefs.SettingsStore
import com.quntodo.app.data.repo.TodoRepository
import com.quntodo.app.reminders.ReminderScheduler
import com.quntodo.app.rules.RuleEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AppContainer(context: Context) {

    val appContext: Context = context.applicationContext
    val database: AppDatabase = AppDatabase.get(appContext)
    val settingsStore = SettingsStore(appContext)
    val imageStore = ImageStore(appContext)
    val reminderScheduler = ReminderScheduler(appContext)
    val ruleEngine = RuleEngine()
    val aiClient = AiClient()
    val screenshotOcr = ScreenshotOcr(appContext)

    val repository = TodoRepository(
        context = appContext,
        database = database,
        settingsStore = settingsStore,
        imageStore = imageStore,
        ruleEngine = ruleEngine,
        aiClient = aiClient,
        ocr = screenshotOcr,
        reminderScheduler = reminderScheduler
    )

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}
