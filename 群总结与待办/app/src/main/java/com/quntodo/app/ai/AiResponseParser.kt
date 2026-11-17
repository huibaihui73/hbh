package com.quntodo.app.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

object AiResponseParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(
        raw: String,
        validIds: Set<Long>,
        zone: ZoneId = ZoneId.systemDefault(),
        now: Long = System.currentTimeMillis()
    ): AiSummary {
        val objectText = extractFirstJsonObject(raw)
            ?: throw IllegalArgumentException("AI 返回内容中没有 JSON")
        val root = runCatching { json.parseToJsonElement(objectText).jsonObject }
            .getOrElse { throw IllegalArgumentException("AI 返回的 JSON 无法解析") }

        val summary = root.string("summary").orEmpty()
        val todos = root["todos"]?.asArray().orEmpty()

        val drafts = mutableListOf<AiDraft>()
        for (element in todos) {
            val item = runCatching { element.jsonObject }.getOrNull() ?: continue
            val title = item.string("title")?.trim().orEmpty()
            if (title.isBlank()) continue
            val body = item.string("body")?.trim().orEmpty().take(400)
            val deadlineText = item.string("deadline")
            val deadline = parseDeadline(deadlineText, zone, now)
            val hasTime = item.bool("deadline_has_time") ?: deadline?.let { true } ?: false
            val group = item.string("group")?.trim()?.take(40)
            val sourceIds = item["source_message_ids"]?.asArray().orEmpty()
                .mapNotNull { ids -> ids.asPrimitive()?.contentOrNull?.toLongOrNull() }
                .filter { it in validIds }
            val confidence = (item.number("confidence") ?: 0.8).coerceIn(0.0, 1.0)

            drafts += AiDraft(
                title = title.take(40),
                body = body,
                deadlineAt = deadline,
                deadlineHasTime = hasTime,
                groupName = group,
                sourceIds = sourceIds.distinct(),
                confidence = confidence
            )
            if (drafts.size >= 8) break
        }

        return AiSummary(summary, drafts)
    }

    private fun parseDeadline(value: String?, zone: ZoneId, now: Long): Long? {
        if (value.isNullOrBlank()) return null
        val trimmed = value.trim()
        val parsed = runCatching {
            OffsetDateTime.parse(trimmed).toInstant().toEpochMilli()
        }.getOrNull() ?: runCatching {
            LocalDateTime.parse(trimmed).atZone(zone).toInstant().toEpochMilli()
        }.getOrNull() ?: runCatching {
            Instant.parse(trimmed).toEpochMilli()
        }.getOrNull() ?: runCatching {
            LocalDate.parse(trimmed).atTime(23, 59, 59).atZone(zone).toInstant().toEpochMilli()
        }.getOrNull()

        if (parsed == null) return null
        val min = now - 365L * 24 * 3600 * 1000
        val max = now + 5L * 365 * 24 * 3600 * 1000
        return parsed.takeIf { it in min..max }
    }

    private fun extractFirstJsonObject(raw: String): String? {
        val start = raw.indexOf('{')
        if (start < 0) return null
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until raw.length) {
            val c = raw[i]
            if (inString) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
            } else {
                when (c) {
                    '"' -> inString = true
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) return raw.substring(start, i + 1)
                    }
                }
            }
        }
        return null
    }

    private fun JsonObject.string(key: String): String? =
        runCatching { this[key]?.jsonPrimitive?.contentOrNull }.getOrNull()

    private fun JsonObject.bool(key: String): Boolean? =
        runCatching { this[key]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() }.getOrNull()

    private fun JsonObject.number(key: String): Double? =
        runCatching { this[key]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull() }.getOrNull()

    private fun JsonElement.asArray(): List<JsonElement>? =
        runCatching { (this as JsonArray).toList() }.getOrNull()

    private fun JsonElement.asPrimitive(): JsonPrimitive? =
        runCatching { this as JsonPrimitive }.getOrNull()
}
