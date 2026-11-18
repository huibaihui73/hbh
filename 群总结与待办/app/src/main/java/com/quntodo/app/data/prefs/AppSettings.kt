package com.quntodo.app.data.prefs

data class AppSettings(
    val aiEnabled: Boolean = false,
    val apiBaseUrl: String = "https://api.deepseek.com/v1",
    val model: String = "deepseek-chat",
    val apiKey: String = "",
    val reminderLeadMinutes: Int = 60,
    val dateOnlyReminderHour: Int = 9,
    val remindersEnabled: Boolean = true,
    val captureEnabled: Boolean = true,
    val captureQq: Boolean = true,
    val captureWechat: Boolean = true,
    val capturePrivateChats: Boolean = false,
    val groupAllowlist: Set<String> = emptySet(),
    val ignoreKeywords: List<String> = emptyList(),
    val onboarded: Boolean = false,
    val diagnosticsEnabled: Boolean = false
) {
    val aiReady: Boolean get() = aiEnabled && apiKey.isNotBlank()
}
