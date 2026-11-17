package com.quntodo.app.rules

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * 中文日期/时间解析。所有相对时间以「消息发送时间」为基准。
 * 只解析出日期时，默认落在当天 23:59:59（截止语义）。
 */
class ChineseDateParser(private val zone: ZoneId = ZoneId.systemDefault()) {

    data class ParsedDate(val at: Long, val hasTime: Boolean, val matched: String)

    private data class DateHit(
        val date: LocalDate,
        val start: Int,
        val end: Int,
        val implicitTime: LocalTime?,
        val text: String,
        val eveningHint: Boolean = false,
        val morningHint: Boolean = false
    )

    private data class TimeHit(
        val hour: Int,
        val minute: Int,
        val start: Int,
        val end: Int,
        val period: String
    )

    fun parse(text: String, reference: Long): ParsedDate? {
        if (text.isBlank()) return null
        val ref = Instant.ofEpochMilli(reference).atZone(zone)
        val today = ref.toLocalDate()
        val dateHits = findDates(text, today)
        val timeHits = findTimes(text, ref.toLocalTime())
        if (dateHits.isEmpty() && timeHits.isEmpty()) return null

        data class Candidate(val at: Long, val hasTime: Boolean, val score: Double, val text: String)

        val candidates = mutableListOf<Candidate>()

        if (dateHits.isEmpty()) {
            for (time in timeHits) {
                var dateTime = today.atTime(time.hour, time.minute)
                if (dateTime.isBefore(ref.toLocalDateTime())) {
                    dateTime = dateTime.plusDays(1)
                }
                candidates += Candidate(
                    dateTime.atZone(zone).toInstant().toEpochMilli(),
                    hasTime = true,
                    score = 0.0,
                    text = "今天/明天 ${pad(time.hour)}:${pad(time.minute)}"
                )
            }
        } else {
            for (hit in dateHits) {
                val nearest = timeHits.minByOrNull { distance(hit, it) }
                val useTime = nearest != null && distance(hit, nearest) <= 10
                var hour = nearest?.hour ?: hit.implicitTime?.hour ?: 23
                val minute = when {
                    nearest != null && useTime -> nearest.minute
                    hit.implicitTime != null -> hit.implicitTime.minute
                    else -> 59
                }
                val second = if (useTime || hit.implicitTime != null) 0 else 59
                if (useTime && hit.eveningHint && hour in 1..11) hour += 12
                if (useTime && hit.morningHint && hour == 12) hour = 0
                if (hour !in 0..23 || minute !in 0..59) continue

                val local = hit.date.atTime(LocalTime.of(hour, minute, second))
                val at = local.atZone(zone).toInstant().toEpochMilli()
                val hasTime = useTime || hit.implicitTime != null
                val score = (if (useTime) 2.0 else 0.0) +
                    (if (hasDeadlineMarker(text, hit.start, hit.end)) 1.0 else 0.0) +
                    (if (hit.implicitTime != null) 0.8 else 0.0)

                candidates += Candidate(at, hasTime, score, hit.text)
            }
        }

        return candidates
            .sortedWith(compareByDescending<Candidate> { it.score }.thenBy { it.at })
            .firstOrNull()
            ?.let { ParsedDate(it.at, it.hasTime, it.text) }
    }

    private fun findDates(text: String, today: LocalDate): List<DateHit> {
        val hits = mutableListOf<DateHit>()
        val covered = mutableListOf<IntRange>()

        fun add(hit: DateHit) {
            hits += hit
            covered += hit.start until hit.end
        }

        fullDate.findAll(text).forEach { m ->
            val y = m.groupValues[1].toIntOrNull() ?: return@forEach
            val mo = m.groupValues[2].toIntOrNull() ?: return@forEach
            val d = m.groupValues[3].toIntOrNull() ?: return@forEach
            val date = runCatching { LocalDate.of(y, mo, d) }.getOrNull() ?: return@forEach
            add(DateHit(date, m.range.first, m.range.last + 1, null, m.value))
        }

        nextMonthDay.findAll(text).forEach { m ->
            if (overlaps(m.range, covered)) return@forEach
            val day = m.groupValues[1].toIntOrNull() ?: return@forEach
            val base = today.plusMonths(1)
            val date = runCatching { LocalDate.of(base.year, base.monthValue, day) }.getOrNull()
                ?: return@forEach
            add(DateHit(date, m.range.first, m.range.last + 1, null, m.value))
        }

        monthDay.findAll(text).forEach { m ->
            if (overlaps(m.range, covered)) return@forEach
            val mo = m.groupValues[1].toIntOrNull() ?: return@forEach
            val d = m.groupValues[2].toIntOrNull() ?: return@forEach
            var date = runCatching { LocalDate.of(today.year, mo, d) }.getOrNull() ?: return@forEach
            if (date.isBefore(today.minusDays(60))) date = date.plusYears(1)
            add(DateHit(date, m.range.first, m.range.last + 1, null, m.value))
        }

        monthEnd.findAll(text).forEach { m ->
            if (overlaps(m.range, covered)) return@forEach
            val prefix = m.groupValues[1]
            val base = if (prefix.startsWith("下")) today.plusMonths(1) else today
            val date = base.withDayOfMonth(base.lengthOfMonth())
            add(DateHit(date, m.range.first, m.range.last + 1, null, m.value))
        }

        weekRange.findAll(text).forEach { m ->
            if (overlaps(m.range, covered)) return@forEach
            val prefix = m.groupValues[1]
            val sundayOfThisWeek = today.plusDays((7 - today.dayOfWeek.value).toLong())
            val date = if (prefix == "下") sundayOfThisWeek.plusDays(7) else sundayOfThisWeek
            add(DateHit(date, m.range.first, m.range.last + 1, null, m.value))
        }

        weekday.findAll(text).forEach { m ->
            if (overlaps(m.range, covered)) return@forEach
            val prefix = m.groupValues[1]
            val target = weekdayValue(m.groupValues[2]) ?: return@forEach
            val todayDow = today.dayOfWeek.value
            var delta = target - todayDow
            when {
                prefix == "下" -> delta += 7
                prefix == "本" || prefix == "这" -> Unit
                else -> if (delta < 0) delta += 7
            }
            add(DateHit(today.plusDays(delta.toLong()), m.range.first, m.range.last + 1, null, m.value))
        }

        relativeDay.findAll(text).forEach { m ->
            if (overlaps(m.range, covered)) return@forEach
            val word = m.value
            val offset = when {
                word == "大后天" -> 3L
                word == "后天" -> 2L
                word.startsWith("明") -> 1L
                else -> 0L
            }
            val evening = word.contains("晚") || word.contains("夜")
            val morning = word.contains("早")
            val implicit = when {
                evening -> LocalTime.of(20, 0)
                morning -> LocalTime.of(8, 0)
                else -> null
            }
            add(
                DateHit(
                    today.plusDays(offset),
                    m.range.first,
                    m.range.last + 1,
                    implicit,
                    word,
                    eveningHint = evening,
                    morningHint = morning
                )
            )
        }

        daysLater.findAll(text).forEach { m ->
            if (overlaps(m.range, covered)) return@forEach
            val days = toInt(m.groupValues[1]) ?: return@forEach
            if (days in 0..365) {
                add(DateHit(today.plusDays(days.toLong()), m.range.first, m.range.last + 1, null, m.value))
            }
        }

        dayOnly.findAll(text).forEach { m ->
            if (overlaps(m.range, covered)) return@forEach
            val day = m.groupValues[1].toIntOrNull() ?: return@forEach
            if (day !in 1..31) return@forEach
            var date = runCatching { LocalDate.of(today.year, today.monthValue, day) }.getOrNull()
            if (date == null) {
                val next = today.plusMonths(1)
                date = runCatching { LocalDate.of(next.year, next.monthValue, day) }.getOrNull()
            }
            if (date == null) return@forEach
            if (date.isBefore(today)) {
                val next = today.plusMonths(1)
                date = runCatching { LocalDate.of(next.year, next.monthValue, day) }.getOrNull()
                    ?: return@forEach
            }
            add(DateHit(date, m.range.first, m.range.last + 1, null, m.value))
        }

        return hits
    }

    private fun findTimes(text: String, refTime: LocalTime): List<TimeHit> {
        val hits = mutableListOf<TimeHit>()
        val covered = mutableListOf<IntRange>()

        pointTime.findAll(text).forEach { m ->
            val period = m.groupValues[1]
            var hour = toInt(m.groupValues[2]) ?: return@forEach
            if (hour !in 0..23) return@forEach
            val special = m.groupValues[3]
            val minute = when (special) {
                "半" -> 30
                "一刻" -> 15
                "三刻" -> 45
                else -> toInt(m.groupValues[4]) ?: 0
            }
            if (minute !in 0..59) return@forEach
            hour = adjustPeriod(hour, period, refTime)
            if (hour !in 0..23) return@forEach
            hits += TimeHit(hour, minute, m.range.first, m.range.last + 1, period)
            covered += m.range
        }

        colonTime.findAll(text).forEach { m ->
            if (overlaps(m.range, covered)) return@forEach
            val hour = m.groupValues[1].toIntOrNull() ?: return@forEach
            val minute = m.groupValues[2].toIntOrNull() ?: return@forEach
            if (hour in 0..23 && minute in 0..59) {
                hits += TimeHit(hour, minute, m.range.first, m.range.last + 1, "")
            }
        }

        return hits
    }

    private fun adjustPeriod(hour: Int, period: String, refTime: LocalTime): Int = when {
        period == "下午" || period == "傍晚" || period == "晚上" || period == "夜里" ->
            if (hour in 1..11) hour + 12 else hour
        period == "中午" -> if (hour in 1..10) hour + 12 else hour
        period == "凌晨" -> if (hour == 12) 0 else hour
        period.isEmpty() && hour in 6..11 && refTime.hour >= 12 -> hour + 12
        else -> hour
    }

    private fun distance(date: DateHit, time: TimeHit): Int = when {
        time.start >= date.end -> time.start - date.end
        date.start >= time.end -> date.start - time.end
        else -> 0
    }

    private fun hasDeadlineMarker(text: String, start: Int, end: Int): Boolean {
        val from = (start - 12).coerceAtLeast(0)
        val to = (end + 12).coerceAtMost(text.length)
        val window = text.substring(from, to)
        return Regex("(截止|之前|以前|请于|最晚|最后|务必|限期|于|前)").containsMatchIn(window)
    }

    private fun overlaps(range: IntRange, covered: List<IntRange>): Boolean =
        covered.any { it.first <= range.last && range.first <= it.last }

    private fun weekdayValue(ch: String): Int? = when (ch) {
        "一" -> 1
        "二" -> 2
        "三" -> 3
        "四" -> 4
        "五" -> 5
        "六" -> 6
        "日", "天", "7" -> 7
        else -> ch.toIntOrNull()?.takeIf { it in 1..7 }
    }

    private fun toInt(raw: String): Int? {
        val s = raw.trim()
        if (s.isEmpty()) return null
        s.toIntOrNull()?.let { return it }
        val digits = mapOf(
            '零' to 0, '一' to 1, '二' to 2, '两' to 2, '三' to 3, '四' to 4,
            '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9
        )
        if (s == "十") return 10
        if (s.length == 1) return digits[s[0]]
        val tenIndex = s.indexOf('十')
        if (tenIndex >= 0) {
            val tens = if (tenIndex == 0) 1 else digits[s[0]] ?: return null
            val ones = if (tenIndex == s.length - 1) 0 else digits[s[tenIndex + 1]] ?: return null
            return tens * 10 + ones
        }
        return null
    }

    private fun pad(value: Int): String = if (value < 10) "0$value" else value.toString()

    private companion object {
        val fullDate = Regex("(20\\d{2})\\s*[-/年]\\s*(\\d{1,2})\\s*[-/月]\\s*(\\d{1,2})\\s*[日号]?")
        val nextMonthDay = Regex("下个?月\\s*(\\d{1,2})\\s*[日号]")
        val monthDay = Regex("(\\d{1,2})\\s*月\\s*(\\d{1,2})\\s*[日号]?")
        val monthEnd = Regex("(本|这个|下个?)?\\s*月\\s*(?:底|末)")
        val dayOnly = Regex("(?<![\\d年月])(\\d{1,2})\\s*[日号](?!楼|房|桌|线|机|车|门|院|店|段|栋)")
        val weekRange = Regex("(本|这|下)?\\s*(?:周|星期|礼拜)\\s*内")
        val weekday = Regex("(本|这|下)?\\s*(?:周|星期|礼拜)\\s*([一二三四五六日天1-7])")
        val relativeDay = Regex("大后天|明天早上|明天晚上|今天晚上|今天早上|后天|明天|明日|今天|今日|今晚|今夜|今早|明早|明晚")
        val daysLater = Regex("(\\d{1,2}|[零一二三四五六七八九十两]{1,3})\\s*天\\s*(?:以)?后")
        val pointTime = Regex(
            "(上午|早上|早晨|中午|下午|傍晚|晚上|夜里|凌晨)?\\s*" +
                "(?<![\\d])(\\d{1,2}|[零一二三四五六七八九十两]{1,3})\\s*[点时]" +
                "(?:(半|一刻|三刻)|(\\d{1,2}|[零一二三四五六七八九十两]{1,3})\\s*分?)?"
        )
        val colonTime = Regex("(?<![\\d])([01]?\\d|2[0-3])\\s*[:：]\\s*([0-5]\\d)(?![\\d])")
    }
}
