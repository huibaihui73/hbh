package com.quntodo.app.util

object RuntimeState {
    @Volatile var listenerConnected: Boolean = false
    @Volatile var lastCaptureAt: Long = 0L
    @Volatile var lastCaptureCount: Int = 0
    @Volatile var lastError: String? = null
    @Volatile var genericNotificationSeen: Boolean = false
    @Volatile var aiLastError: String? = null

    fun error(message: String) {
        lastError = message.take(300)
    }

    fun snapshot(): String = buildString {
        appendLine("监听服务：${if (listenerConnected) "已连接" else "未连接"}")
        appendLine("最近抓取：${if (lastCaptureAt == 0L) "无" else TimeFormat.relative(lastCaptureAt)}（${lastCaptureCount} 条）")
        if (genericNotificationSeen) appendLine("提示：收到过不显示内容的 QQ 通知，请在 QQ 设置里打开「显示消息详情」")
        appendLine("最近错误：${lastError ?: "无"}")
        append("AI 最近错误：${aiLastError ?: "无"}")
    }
}
