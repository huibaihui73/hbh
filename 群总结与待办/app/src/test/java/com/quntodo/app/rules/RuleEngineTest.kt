package com.quntodo.app.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class RuleEngineTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val reference: Long =
        ZonedDateTime.of(2026, 11, 18, 14, 30, 0, 0, zone).toInstant().toEpochMilli()
    private val engine = RuleEngine(ChineseDateParser(zone))

    private fun message(id: Long, content: String, postedAt: Long = reference) =
        RuleEngine.SourceMessage(
            id = id,
            groupKey = "qq|计科2301",
            groupName = "计科2301",
            sender = "班长",
            content = content,
            postedAt = postedAt
        )

    @Test
    fun `notice with deadline becomes one todo`() {
        val drafts = engine.extract(
            listOf(message(1, "请大家周五前把报名表交到班长那里，记得带学生证。")),
            now = reference
        )
        assertEquals(1, drafts.size)
        val deadline = drafts.first().deadlineAt
        assertTrue(deadline != null)
        val date = Instant.ofEpochMilli(deadline!!).atZone(zone).toLocalDate()
        assertEquals(LocalDate.of(2026, 11, 20), date)
    }

    @Test
    fun `pure chatter yields nothing`() {
        val drafts = engine.extract(
            listOf(
                message(1, "哈哈"),
                message(2, "收到"),
                message(3, "好的谢谢"),
                message(4, "明天见"),
                message(5, "[表情]")
            ),
            now = reference
        )
        assertEquals(0, drafts.size)
    }

    @Test
    fun `relay signup is one todo with names in body`() {
        val drafts = engine.extract(
            listOf(message(1, "接龙报名：\n1. 张三\n2. 李四\n3. 王五")),
            now = reference
        )
        assertEquals(1, drafts.size)
        assertTrue(drafts.first().body.contains("张三"))
    }

    @Test
    fun `a burst of messages merges into few todos`() {
        val contents = listOf(
            "周五下午3点在A101开会，请大家准时参加",
            "周五下午3点开会，地点A101，别迟到",
            "周五开会地点还是A101",
            "记得带笔记本和笔",
            "会议内容是关于期末安排",
            "请大家提前十分钟到",
            "收到请回复",
            "会议大概一个小时",
            "周五A101见",
            "有事的提前说",
            "带好学生证",
            "另外请大家周五前把材料交齐"
        )
        val drafts = engine.extract(
            contents.mapIndexed { index, content -> message(index + 1L, content, reference + index * 60_000L) },
            now = reference
        )
        assertTrue("应该合并成少量待办，实际 ${drafts.size}", drafts.size in 1..5)
    }

    @Test
    fun `duplicate messages do not create duplicates`() {
        val text = "请于11月20日前提交报名表"
        val drafts = engine.extract(
            listOf(message(1, text), message(2, text)),
            now = reference
        )
        assertEquals(1, drafts.size)
    }

    @Test
    fun `mention only is not a separate todo`() {
        val drafts = engine.extract(
            listOf(message(1, "@全体成员 另外下周一上午9点在A101开班会，请大家不要迟到。")),
            now = reference
        )
        assertEquals(1, drafts.size)
        val deadline = drafts.first().deadlineAt
        assertTrue(deadline != null)
        val date = Instant.ofEpochMilli(deadline!!).atZone(zone).toLocalDate()
        assertEquals(LocalDate.of(2026, 11, 23), date)
    }

    @Test
    fun `finished event is ignored`() {
        val drafts = engine.extract(
            listOf(message(1, "报名已截止，活动已结束")),
            now = reference
        )
        assertEquals(0, drafts.size)
    }
}
