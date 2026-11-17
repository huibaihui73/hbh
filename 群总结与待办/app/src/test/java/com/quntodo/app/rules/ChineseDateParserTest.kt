package com.quntodo.app.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class ChineseDateParserTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val reference: Long =
        ZonedDateTime.of(2026, 11, 18, 14, 30, 0, 0, zone).toInstant().toEpochMilli()
    private val parser = ChineseDateParser(zone)

    private fun parse(text: String): ChineseDateParser.ParsedDate? = parser.parse(text, reference)

    private fun local(ts: Long): LocalDateTime =
        Instant.ofEpochMilli(ts).atZone(zone).toLocalDateTime()

    @Test
    fun `today with afternoon time`() {
        val result = parse("今天下午3点开会")!!
        assertTrue(result.hasTime)
        assertEquals(LocalDateTime.of(2026, 11, 18, 15, 0), local(result.at))
    }

    @Test
    fun `tomorrow morning time`() {
        val result = parse("明天上午9点集合")!!
        assertTrue(result.hasTime)
        assertEquals(LocalDateTime.of(2026, 11, 19, 9, 0), local(result.at))
    }

    @Test
    fun `friday deadline is end of day`() {
        // 2026-11-18 is Wednesday
        val result = parse("周五前交报名表")!!
        assertFalse(result.hasTime)
        assertEquals(LocalDate.of(2026, 11, 20), local(result.at).toLocalDate())
        assertEquals(23, local(result.at).hour)
    }

    @Test
    fun `next monday`() {
        val result = parse("下周一交材料")!!
        assertEquals(LocalDate.of(2026, 11, 23), local(result.at).toLocalDate())
    }

    @Test
    fun `tonight eight is evening`() {
        val result = parse("今晚8点活动开始")!!
        assertTrue(result.hasTime)
        assertEquals(LocalDateTime.of(2026, 11, 18, 20, 0), local(result.at))
    }

    @Test
    fun `bare eight after noon means eight pm`() {
        val result = parse("8点开会")!!
        assertEquals(20, local(result.at).hour)
    }

    @Test
    fun `three days later`() {
        val result = parse("3天后截止")!!
        assertEquals(LocalDate.of(2026, 11, 21), local(result.at).toLocalDate())
    }

    @Test
    fun `month end`() {
        val result = parse("月底前提交")!!
        assertEquals(LocalDate.of(2026, 11, 30), local(result.at).toLocalDate())
    }

    @Test
    fun `recent past month day stays expired`() {
        val result = parse("11月5日交过表")!!
        assertEquals(LocalDate.of(2026, 11, 5), local(result.at).toLocalDate())
    }

    @Test
    fun `far past month day rolls to next year`() {
        val result = parse("1月5日开学")!!
        assertEquals(LocalDate.of(2027, 1, 5), local(result.at).toLocalDate())
    }

    @Test
    fun `building number is not a date`() {
        assertNull(parse("到5号楼集合"))
    }

    @Test
    fun `chinese hour with quarter`() {
        val result = parse("八点一刻开门")!!
        assertTrue(result.hasTime)
        assertEquals(20, local(result.at).hour)
        assertEquals(15, local(result.at).minute)
    }

    @Test
    fun `colon time`() {
        val result = parse("明天20:00直播")!!
        assertEquals(LocalDateTime.of(2026, 11, 19, 20, 0), local(result.at))
    }

    @Test
    fun `next month day`() {
        val result = parse("下个月5号缴费")!!
        assertEquals(LocalDate.of(2026, 12, 5), local(result.at).toLocalDate())
    }

    @Test
    fun `midnight`() {
        val result = parse("凌晨12点断网")!!
        assertEquals(0, local(result.at).hour)
    }

    @Test
    fun `no date returns null`() {
        assertNull(parse("大家下午好"))
    }

    @Test
    fun `deadline marker with explicit time`() {
        val result = parse("截止到周五下午6点")!!
        assertEquals(LocalDateTime.of(2026, 11, 20, 18, 0), local(result.at))
    }

    @Test
    fun `this week inside means sunday`() {
        val result = parse("本周内填完")!!
        assertEquals(LocalDate.of(2026, 11, 22), local(result.at).toLocalDate())
    }

    @Test
    fun `next week inside means next sunday`() {
        val result = parse("下周内交齐")!!
        assertEquals(LocalDate.of(2026, 11, 29), local(result.at).toLocalDate())
    }

    @Test
    fun `tomorrow still parses as date`() {
        val result = parse("明天见")!!
        assertNotNull(result)
        assertEquals(LocalDate.of(2026, 11, 19), local(result.at).toLocalDate())
    }
}
