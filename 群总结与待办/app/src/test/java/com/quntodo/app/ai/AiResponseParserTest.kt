package com.quntodo.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Assert.assertThrows
import java.time.OffsetDateTime
import java.time.ZoneId

class AiResponseParserTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val validIds = setOf(1L, 2L, 3L)

    @Test
    fun `parses fenced json and filters invalid ids`() {
        val raw = """
            ```json
            {"summary":"一件事","todos":[{"title":"交报名表","body":"周五前","deadline":"2026-11-20T18:00:00+08:00","deadline_has_time":true,"group":"计科","source_message_ids":[1,99],"confidence":0.9}]}
            ```
        """.trimIndent()

        val result = AiResponseParser.parse(raw, validIds, zone)
        assertEquals(1, result.drafts.size)
        assertEquals("交报名表", result.drafts.first().title)
        assertEquals(listOf(1L), result.drafts.first().sourceIds)
        assertTrue(result.drafts.first().deadlineHasTime)
        assertEquals(
            OffsetDateTime.parse("2026-11-20T18:00:00+08:00").toInstant().toEpochMilli(),
            result.drafts.first().deadlineAt
        )
    }

    @Test
    fun `parses json surrounded by prose`() {
        val raw = "好的，结果如下：{\"summary\":\"x\",\"todos\":[{\"title\":\"缴费\",\"body\":\"\",\"deadline\":null,\"source_message_ids\":[2],\"confidence\":0.5}]} 希望有帮助"
        val result = AiResponseParser.parse(raw, validIds, zone)
        assertEquals(1, result.drafts.size)
        assertEquals(listOf(2L), result.drafts.first().sourceIds)
        assertEquals(null, result.drafts.first().deadlineAt)
    }

    @Test
    fun `skips items without title`() {
        val raw = """{"summary":"x","todos":[{"body":"没有标题"},{"title":"有效事项","body":"","deadline":null,"source_message_ids":[]}]}"""
        val result = AiResponseParser.parse(raw, validIds, zone)
        assertEquals(1, result.drafts.size)
        assertEquals("有效事项", result.drafts.first().title)
    }

    @Test
    fun `empty todos is valid`() {
        val result = AiResponseParser.parse("""{"summary":"都是闲聊","todos":[]}""", validIds, zone)
        assertTrue(result.drafts.isEmpty())
        assertEquals("都是闲聊", result.summary)
    }

    @Test
    fun `invalid json throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            AiResponseParser.parse("not json at all", validIds, zone)
        }
    }

    @Test
    fun `date only deadline is accepted`() {
        val raw = """{"summary":"x","todos":[{"title":"交表","deadline":"2026-11-25"}]}"""
        val result = AiResponseParser.parse(raw, validIds, zone)
        assertNotNull(result.drafts.first().deadlineAt)
    }
}
