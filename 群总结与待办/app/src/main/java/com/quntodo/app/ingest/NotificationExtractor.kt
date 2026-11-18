package com.quntodo.app.ingest

import android.app.Notification
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.quntodo.app.rules.JunkFilter

class NotificationExtractor {

    data class Part(val sender: String, val content: String, val postedAt: Long)

    data class Result(
        val isGroup: Boolean,
        val groupName: String,
        val parts: List<Part>,
        val generic: Boolean
    )

    fun extract(sbn: StatusBarNotification): Result? {
        if (sbn.isOngoing) return null
        val notification = sbn.notification ?: return null
        val extras = notification.extras

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim().orEmpty()
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.trim().orEmpty()
        val conversation = extras.getCharSequence(NotificationCompat.EXTRA_CONVERSATION_TITLE)
            ?.toString()?.trim().orEmpty()

        val body = if (bigText.length > text.length) bigText else text

        val style = runCatching {
            NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
        }.getOrNull()

        val styleTitle = style?.conversationTitle?.toString()?.trim().orEmpty()
        val styleIsGroup = style?.isGroupConversation == true
        val styleParts = style?.messages.orEmpty().mapNotNull { message ->
            val content = message.text?.toString()?.trim().orEmpty()
            if (content.isEmpty()) return@mapNotNull null
            @Suppress("DEPRECATION")
            val sender = message.sender?.toString()?.trim().orEmpty()
            val timestamp = message.timestamp.takeIf { it > 0 } ?: sbn.postTime
            Part(sender, content, timestamp)
        }

        // QQ 的「我的电脑 / 我的平板」等设备消息：标题是「QQ」，正文是「设备名: 内容」。
        // 单独识别成对应设备会话，避免不同设备共用同一个通知编号时互相去重。
        if (styleParts.isEmpty()) {
            deviceMessage(title, body)?.let { (deviceName, content) ->
                return Result(
                    isGroup = false,
                    groupName = deviceName,
                    parts = listOf(Part(deviceName, content, sbn.postTime)),
                    generic = false
                )
            }
        }

        val generic = JunkFilter.isGenericNotification(body) || JunkFilter.isGenericNotification(text)
        val inline = inlineSender(body)

        var score = 0
        if (styleIsGroup) score += 3
        if (conversation.isNotBlank() && conversation != title) score += 2
        if (subText.contains("群")) score += 2
        if (Regex("\\(\\d+\\)$").containsMatchIn(title)) score += 2
        if (title.contains("群")) score += 2
        if (inline != null && inline.first != title) score += 2
        if (subText.contains("私聊") || subText.contains("好友")) score -= 4

        val isGroup = styleIsGroup || score >= 2

        val groupNameRaw = when {
            styleTitle.isNotBlank() -> styleTitle
            conversation.isNotBlank() -> conversation
            else -> title
        }
            .replace(Regex("\\(?\\d+\\s*条新消息\\)?$"), "")
            .replace(Regex("[（(]\\d+[)）]$"), "")
            .trim()

        val parts: List<Part> = when {
            styleParts.isNotEmpty() -> styleParts
            isGroup && inline != null -> listOf(Part(inline.first, inline.second, sbn.postTime))
            isGroup -> splitLines(body)
                .map { Part(it.first, it.second, sbn.postTime) }
                .ifEmpty { listOf(Part("", body, sbn.postTime)) }
            else -> listOf(Part(title, body, sbn.postTime)).filter { it.content.isNotBlank() }
        }

        if (parts.isEmpty() && !generic) return null

        return Result(
            isGroup = isGroup,
            groupName = groupNameRaw.ifBlank { "未知会话" },
            parts = parts,
            generic = generic
        )
    }

    private fun splitLines(body: String): List<Pair<String, String>> {
        val lines = body.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.size <= 1) return emptyList()
        val parsed = lines.mapNotNull { inlineSender(it) }
        return if (parsed.size == lines.size) parsed else emptyList()
    }

    private fun deviceMessage(title: String, body: String): Pair<String, String>? {
        if (!title.startsWith("QQ")) return null
        val firstLine = body.lineSequence().firstOrNull()?.trim().orEmpty()
        val match = DEVICE_PREFIX.find(firstLine) ?: return null
        val deviceName = match.groupValues[1]
        val content = match.groupValues[2].trim()
        if (content.isEmpty()) return null
        return deviceName to content
    }

    private fun inlineSender(text: String): Pair<String, String>? {
        val match = Regex("^([^:：\\n]{1,20})\\s*[:：]\\s*(.+)$", RegexOption.DOT_MATCHES_ALL)
            .find(text.trim()) ?: return null
        val sender = match.groupValues[1].trim()
        val content = match.groupValues[2].trim()
        if (sender.isEmpty() || content.isEmpty()) return null
        if (content.length < 2) return null
        return sender to content
    }

    private companion object {
        val DEVICE_PREFIX = Regex(
            "^(我的电脑|我的平板|我的手机|我的设备|文件传输助手)\\s*[:：]\\s*(.+)$"
        )
    }
}
