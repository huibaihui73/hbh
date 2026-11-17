package com.quntodo.app.rules

object JunkFilter {

    private val mediaOnly = Regex(
        "^\\s*\\[(图片|表情|语音|视频|文件|红包|动画表情|位置|链接|小程序|名片|视频通话|语音通话)\\]\\s*$"
    )

    private val genericMessage = listOf(
        Regex("^\\s*你?有?\\s*\\d*\\s*条新消息\\s*$"),
        Regex("^\\s*你收到一?条(新)?消息\\s*$"),
        Regex("^\\s*收到\\d+条新消息\\s*$")
    )

    private val ackOnly = Regex(
        "^\\s*(收到|好的|好|嗯+|哦+|ok|OK|1|\\+1|谢谢|感谢|辛苦了|知道了|明白了|没问题|可以|行|是的|对的|哈哈哈*|666|👍)[\\s!！。.~～]*$"
    )

    private val shortNoise = listOf(
        "撤回了一条消息", "加入群聊", "加入了群聊", "修改群名为", "正在输入",
        "历史消息", "查看更多", "点击查看", "消息已发出"
    )

    fun isAckOnly(text: String): Boolean = ackOnly.matches(text.trim())

    fun isGenericNotification(text: String): Boolean =
        genericMessage.any { it.matches(text.trim()) }

    fun isJunkMessage(text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty()) return true
        if (mediaOnly.matches(t)) return true
        if (isAckOnly(t)) return true
        if (isGenericNotification(t)) return true
        if (t.length <= 24 && shortNoise.any { t.contains(it) }) return true
        return false
    }

    /** 群公告、通知类消息不要按闲聊过滤掉。 */
    fun looksLikeNotice(text: String): Boolean {
        val t = text.trim()
        return t.startsWith("【") || t.startsWith("[公告]") ||
            t.contains("通知") || t.contains("公告") || t.contains("@全体成员")
    }
}
