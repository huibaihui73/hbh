package com.quntodo.app.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object RuntimeState {

    private val _listenerConnected = MutableStateFlow(false)
    val listenerConnectedFlow: StateFlow<Boolean> = _listenerConnected.asStateFlow()

    @Volatile
    var listenerConnected: Boolean = false
        private set

    @Volatile var lastCaptureAt: Long = 0L
    @Volatile var lastCaptureCount: Int = 0
    @Volatile var lastError: String? = null
    @Volatile var genericNotificationSeen: Boolean = false
    @Volatile var aiLastError: String? = null

    fun setListenerConnected(connected: Boolean) {
        listenerConnected = connected
        _listenerConnected.value = connected
    }

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
