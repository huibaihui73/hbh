package com.quntodo.app.service

import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.quntodo.app.BuildConfig
import com.quntodo.app.QunTodoApp
import com.quntodo.app.data.db.entity.RawMessageEntity
import com.quntodo.app.data.repo.TodoRepository
import com.quntodo.app.ingest.NotificationExtractor
import com.quntodo.app.rules.JunkFilter
import com.quntodo.app.util.RuntimeState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class QqNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val extractor = NotificationExtractor()

    override fun onListenerConnected() {
        super.onListenerConnected()
        RuntimeState.setListenerConnected(true)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        RuntimeState.setListenerConnected(false)
        runCatching {
            NotificationListenerService.requestRebind(
                ComponentName(this, QqNotificationListener::class.java)
            )
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        if (sbn.packageName !in supportedPackages()) return
        scope.launch {
            runCatching { handle(sbn) }.onFailure {
                RuntimeState.error("处理通知失败：${it.message}")
            }
        }
    }

    private suspend fun handle(sbn: StatusBarNotification) {
        val app = application as? QunTodoApp ?: return
        val settings = app.container.settingsStore.current()
        if (!settings.captureEnabled) return
        if (sbn.packageName == "com.tencent.mm") {
            if (!settings.captureWechat) return
        } else if (!settings.captureQq) {
            return
        }

        val extracted = extractor.extract(sbn) ?: return
        if (extracted.generic) {
            RuntimeState.genericNotificationSeen = true
            return
        }
        if (!extracted.isGroup && !settings.capturePrivateChats) return

        val groupName = extracted.groupName.ifBlank { "未知群聊" }
        if (extracted.isGroup &&
            settings.groupAllowlist.isNotEmpty() &&
            groupName !in settings.groupAllowlist
        ) {
            return
        }

        val messages = extracted.parts.mapNotNull { part ->
            val content = part.content.trim()
            if (content.isEmpty()) return@mapNotNull null
            if (JunkFilter.isJunkMessage(content)) return@mapNotNull null
            TodoRepository.IncomingMessage(
                packageName = sbn.packageName,
                groupName = groupName,
                sender = part.sender,
                content = content,
                postedAt = part.postedAt,
                notificationKey = sbn.key,
                source = RawMessageEntity.SOURCE_NOTIFICATION
            )
        }
        if (messages.isEmpty()) return

        val inserted = app.container.repository.ingestIncoming(messages, settings)
        RuntimeState.lastCaptureAt = System.currentTimeMillis()
        RuntimeState.lastCaptureCount = inserted
    }

    private fun supportedPackages(): Set<String> = buildSet {
        addAll(BASE_PACKAGES)
        if (BuildConfig.DEBUG) add("com.android.shell")
    }

    override fun onDestroy() {
        scope.cancel()
        RuntimeState.setListenerConnected(false)
        super.onDestroy()
    }

    companion object {
        private val BASE_PACKAGES = setOf(
            "com.tencent.mobileqq",
            "com.tencent.tim",
            "com.tencent.qqlite",
            "com.tencent.mm"
        )
    }
}
