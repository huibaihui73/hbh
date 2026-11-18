package com.quntodo.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.quntodo.app.data.security.SecureKeyStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "quntodo_settings")

class SettingsStore(private val context: Context) {

    private val secure = SecureKeyStore()

    private object Keys {
        val aiEnabled = booleanPreferencesKey("ai_enabled")
        val apiBaseUrl = stringPreferencesKey("api_base_url")
        val model = stringPreferencesKey("model")
        val apiKeyEnc = stringPreferencesKey("api_key_enc")
        val reminderLead = intPreferencesKey("reminder_lead_minutes")
        val dateOnlyHour = intPreferencesKey("date_only_hour")
        val remindersEnabled = booleanPreferencesKey("reminders_enabled")
        val captureEnabled = booleanPreferencesKey("capture_enabled")
        val captureQq = booleanPreferencesKey("capture_qq")
        val captureWechat = booleanPreferencesKey("capture_wechat")
        val capturePrivate = booleanPreferencesKey("capture_private")
        val groupAllowlist = stringSetPreferencesKey("group_allowlist")
        val ignoreKeywords = stringSetPreferencesKey("ignore_keywords")
        val onboarded = booleanPreferencesKey("onboarded")
        val diagnostics = booleanPreferencesKey("diagnostics_enabled")
    }

    val settings: Flow<AppSettings> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs ->
            AppSettings(
                aiEnabled = prefs[Keys.aiEnabled] ?: false,
                apiBaseUrl = prefs[Keys.apiBaseUrl] ?: "https://api.deepseek.com/v1",
                model = prefs[Keys.model] ?: "deepseek-chat",
                apiKey = secure.decrypt(prefs[Keys.apiKeyEnc] ?: ""),
                reminderLeadMinutes = prefs[Keys.reminderLead] ?: 60,
                dateOnlyReminderHour = prefs[Keys.dateOnlyHour] ?: 9,
                remindersEnabled = prefs[Keys.remindersEnabled] ?: true,
                captureEnabled = prefs[Keys.captureEnabled] ?: true,
                captureQq = prefs[Keys.captureQq] ?: true,
                captureWechat = prefs[Keys.captureWechat] ?: true,
                capturePrivateChats = prefs[Keys.capturePrivate] ?: false,
                groupAllowlist = prefs[Keys.groupAllowlist] ?: emptySet(),
                ignoreKeywords = (prefs[Keys.ignoreKeywords] ?: emptySet()).toList().sorted(),
                onboarded = prefs[Keys.onboarded] ?: false,
                diagnosticsEnabled = prefs[Keys.diagnostics] ?: false
            )
        }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setAiEnabled(value: Boolean) = edit { it[Keys.aiEnabled] = value }

    suspend fun setAiConfig(baseUrl: String, model: String, plainKey: String) = edit {
        it[Keys.apiBaseUrl] = baseUrl.trim().ifBlank { "https://api.deepseek.com/v1" }
        it[Keys.model] = model.trim().ifBlank { "deepseek-chat" }
        if (plainKey.isNotBlank()) it[Keys.apiKeyEnc] = secure.encrypt(plainKey.trim())
    }

    suspend fun clearApiKey() = edit { it.remove(Keys.apiKeyEnc) }

    suspend fun setReminderLead(minutes: Int) = edit { it[Keys.reminderLead] = minutes }

    suspend fun setDateOnlyHour(hour: Int) = edit { it[Keys.dateOnlyHour] = hour }

    suspend fun setRemindersEnabled(enabled: Boolean) = edit { it[Keys.remindersEnabled] = enabled }

    suspend fun setCaptureEnabled(value: Boolean) = edit { it[Keys.captureEnabled] = value }

    suspend fun setCaptureQq(value: Boolean) = edit { it[Keys.captureQq] = value }

    suspend fun setCaptureWechat(value: Boolean) = edit { it[Keys.captureWechat] = value }

    suspend fun setCapturePrivate(value: Boolean) = edit { it[Keys.capturePrivate] = value }

    suspend fun setGroupAllowlist(groups: Set<String>) = edit { it[Keys.groupAllowlist] = groups }

    suspend fun setIgnoreKeywords(words: Set<String>) = edit { it[Keys.ignoreKeywords] = words }

    suspend fun setOnboarded(value: Boolean) = edit { it[Keys.onboarded] = value }

    suspend fun setDiagnosticsEnabled(value: Boolean) = edit { it[Keys.diagnostics] = value }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }
}
