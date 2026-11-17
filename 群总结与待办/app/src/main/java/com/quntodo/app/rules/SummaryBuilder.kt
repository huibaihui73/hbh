package com.quntodo.app.rules

import com.quntodo.app.util.TimeFormat

object SummaryBuilder {

    fun local(messageCount: Int, drafts: List<RuleEngine.Draft>): String {
        if (drafts.isEmpty()) {
            return "共 $messageCount 条消息，没有识别到明确的待办。可以在收件箱里挑出需要的消息再整理一次。"
        }
        val earliest = drafts.mapNotNull { it.deadlineAt }.minOrNull()
        val base = "共 $messageCount 条消息，整理出 ${drafts.size} 条待办。"
        return if (earliest != null) {
            base + "最近截止：" + TimeFormat.fullDateTime(earliest) + "。"
        } else {
            base
        }
    }
}
