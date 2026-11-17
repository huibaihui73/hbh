package com.quntodo.app.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object TimeFormat {
    private val zone: ZoneId get() = ZoneId.systemDefault()

    private val monthDayTime = DateTimeFormatter.ofPattern("M月d日 HH:mm", Locale.CHINA)
    private val monthDay = DateTimeFormatter.ofPattern("M月d日", Locale.CHINA)
    private val hourMinute = DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA)
    private val fullDate = DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA)

    fun fullDateTime(ts: Long): String =
        monthDayTime.format(Instant.ofEpochMilli(ts).atZone(zone))

    fun dateOnly(ts: Long): String =
        monthDay.format(Instant.ofEpochMilli(ts).atZone(zone))

    fun timeOnly(ts: Long): String =
        hourMinute.format(Instant.ofEpochMilli(ts).atZone(zone))

    fun relative(ts: Long, now: Long = System.currentTimeMillis()): String {
        val date = Instant.ofEpochMilli(ts).atZone(zone).toLocalDate()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return when {
            date == today -> "今天 ${timeOnly(ts)}"
            date == today.minusDays(1) -> "昨天 ${timeOnly(ts)}"
            date == today.minusDays(2) -> "前天 ${timeOnly(ts)}"
            date.year == today.year -> "${date.monthValue}月${date.dayOfMonth}日 ${timeOnly(ts)}"
            else -> fullDate.format(date.atStartOfDay(zone))
        }
    }

    fun deadline(ts: Long, hasTime: Boolean): String {
        val z = Instant.ofEpochMilli(ts).atZone(zone)
        return if (hasTime) monthDayTime.format(z) else "${monthDay.format(z)} 截止"
    }
}
