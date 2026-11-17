package com.quntodo.app.rules

import com.quntodo.app.util.TextSimilarity
import kotlin.math.abs
import kotlin.math.exp

/**
 * 本地规则引擎：不联网、不花钱，从一批群消息里抽出待办候选。
 */
class RuleEngine(private val dateParser: ChineseDateParser = ChineseDateParser()) {

    data class SourceMessage(
        val id: Long,
        val groupKey: String,
        val groupName: String,
        val sender: String,
        val content: String,
        val postedAt: Long
    )

    data class Draft(
        val title: String,
        val body: String,
        val groupKey: String?,
        val groupName: String?,
        val deadlineAt: Long?,
        val deadlineHasTime: Boolean,
        val sourceRawIds: List<Long>,
        val confidence: Double,
        val source: String = "RULE"
    )

    private data class Item(
        val message: SourceMessage,
        val clause: String,
        val score: Double,
        val date: ChineseDateParser.ParsedDate?
    )

    fun extract(
        messages: List<SourceMessage>,
        now: Long = System.currentTimeMillis(),
        maxDrafts: Int = 12
    ): List<Draft> {
        val groupMessages = messages.groupBy { it.groupKey }
        val drafts = mutableListOf<Draft>()

        for ((_, groupItems) in groupMessages) {
            val items = mutableListOf<Item>()
            for (message in groupItems) {
                val normalized = TextNormalizer.normalize(message.content)
                if (JunkFilter.isJunkMessage(normalized)) continue
                val clauses = splitClauses(normalized)
                for (clause in clauses) {
                    val text = clause.trim()
                    if (text.length < 3) continue
                    if (JunkFilter.isAckOnly(text)) continue
                    val withoutMention = text
                        .replace(Regex("@(全体成员|所有人)"), "")
                        .trim(' ', '，', ',', '、')
                    if (withoutMention.length < 2) continue
                    val score = scoreClause(text)
                    val date = dateParser.parse(text, message.postedAt)
                    val actionable = score >= 4.0 ||
                        (date != null && (score >= 3.0 || containsAction(text) || containsDeadlineWord(text)))
                    if (!actionable) continue
                    items += Item(message, text, score, date)
                }
            }
            for (cluster in cluster(items)) {
                buildDraft(cluster)?.let { drafts += it }
            }
        }

        return dedupeDrafts(drafts)
            .sortedWith(
                compareBy<Draft> { it.deadlineAt == null }
                    .thenBy { it.deadlineAt ?: Long.MAX_VALUE }
                    .thenByDescending { it.confidence }
            )
            .take(maxDrafts)
    }

    private fun splitClauses(text: String): List<String> {
        val primary = text.split(Regex("[\\n。！？；;]+"))
        return primary
            .flatMap { it.split(Regex("[!?]+")) }
            .map { it.trim().trim('，', ',', '、', ' ') }
            .filter { it.isNotEmpty() }
    }

    private fun scoreClause(clause: String): Double {
        var score = 0.0

        fun hit(words: List<String>, weight: Double) {
            var count = 0
            for (word in words) {
                if (word.isNotEmpty() && clause.contains(word, ignoreCase = true)) {
                    score += weight
                    count++
                    if (count >= 3) break
                }
            }
        }

        hit(KeywordLexicon.strong, 3.0)
        hit(KeywordLexicon.medium, 2.0)
        hit(KeywordLexicon.urgent, 2.0)
        hit(KeywordLexicon.weak, 1.0)
        hit(KeywordLexicon.chatter, -3.0)
        hit(KeywordLexicon.finished, -4.0)

        if (clause.length in 6..180) score += 0.5
        if (clause.endsWith("?") || clause.contains("吗") || clause.contains("呢")) score -= 2.0
        if (Regex("^(请大家|请各位|各位|大家|同学们|班长)").containsMatchIn(clause)) score += 1.0
        if (containsDeadlineWord(clause)) score += 1.0
        if (clause.contains("@全体成员") || clause.contains("@所有人")) score += 2.0

        return score
    }

    private fun containsAction(text: String): Boolean =
        KeywordLexicon.containsAny(text, KeywordLexicon.strong) ||
            KeywordLexicon.containsAny(text, KeywordLexicon.medium)

    private fun containsDeadlineWord(text: String): Boolean =
        Regex("(截止|之前|以前|请于|最晚|最后|务必|尽快|前)").containsMatchIn(text)

    private fun cluster(items: List<Item>): List<List<Item>> {
        val sorted = items.sortedBy { it.message.postedAt }
        val clusters = mutableListOf<MutableList<Item>>()

        for (item in sorted) {
            var target: MutableList<Item>? = null
            var bestSimilarity = 0.0

            for (cluster in clusters) {
                val last = cluster.last()
                val gap = item.message.postedAt - last.message.postedAt
                if (gap > 30 * 60_000L) continue

                val similarity = TextSimilarity.jaccard(
                    item.clause,
                    cluster.joinToString(" ") { it.clause }
                )
                val sameDeadline = item.date != null && cluster.any {
                    it.date != null && abs(it.date.at - item.date.at) <= 60_000L
                }
                val mergeWord = KeywordLexicon.mergeWords.any { item.clause.startsWith(it) }

                val matched = similarity >= 0.35 ||
                    (sameDeadline && sharesAction(item.clause, last.clause)) ||
                    mergeWord
                if (matched && (target == null || similarity > bestSimilarity)) {
                    bestSimilarity = similarity
                    target = cluster
                }
            }

            if (target != null) target.add(item) else clusters.add(mutableListOf(item))
        }
        return clusters
    }

    private fun sharesAction(a: String, b: String): Boolean =
        KeywordLexicon.strong.any { it.isNotEmpty() && a.contains(it) && b.contains(it) }

    private fun buildDraft(cluster: List<Item>): Draft? {
        if (cluster.isEmpty()) return null
        val titleItem = cluster.maxByOrNull { it.score + if (it.date != null) 3.0 else 0.0 }
            ?: return null
        val title = cleanTitle(titleItem.clause)
        if (title.isBlank()) return null

        var body = cluster.map { it.clause }
            .distinct()
            .filter { it != titleItem.clause }
            .joinToString("\n")
            .take(600)

        val relayMessage = cluster.map { it.message }.firstOrNull { it.content.contains("接龙") }
        if (relayMessage != null) {
            body = relayMessage.content.take(600)
        } else if (body.isBlank()) {
            body = titleItem.message.content.take(300)
        }

        val date = cluster.mapNotNull { it.date }.minByOrNull { it.at }
        val rawScore = titleItem.score
        val confidence = (1.0 / (1.0 + exp(-((rawScore - 4.0) / 1.8)))).coerceIn(0.3, 0.95)

        return Draft(
            title = title,
            body = body,
            groupKey = titleItem.message.groupKey,
            groupName = titleItem.message.groupName,
            deadlineAt = date?.at,
            deadlineHasTime = date?.hasTime ?: false,
            sourceRawIds = cluster.map { it.message.id }.distinct(),
            confidence = confidence,
            source = "RULE"
        )
    }

    private fun cleanTitle(clause: String): String {
        var text = clause
        text = text.replace(Regex("^(?:【[^】]{1,14}】|\\[[^\\]]{1,14}\\])\\s*"), "")
        text = text.replace(Regex("^(?:通知|公告|提醒|重要)[:：\\s]*"), "")
        text = text.replace(Regex("^@\\S{1,20}\\s+"), "")

        val prefix = Regex("^([^:：\\n]{1,8})\\s*[:：]\\s*(.+)$").find(text)
        if (prefix != null) {
            val head = prefix.groupValues[1].trim()
            val reserved = setOf(
                "截止时间", "报名时间", "活动时间", "时间", "地点", "注意", "要求",
                "说明", "提醒", "内容", "地点时间", "报名方式"
            )
            if (head !in reserved) text = prefix.groupValues[2].trim()
        }

        text = text.trim().trim('，', ',', '。', '.', '!', '！', '?', '？', ' ', '\n')
        if (text.any { it in '一'..'龥' }) {
            text = text.replace(",", "，").replace(";", "；").replace(":", "：")
        }
        if (text.length > 24) {
            text = text.take(24).trimEnd('，', ',', '、', ' ')
        }
        return text
    }

    private fun dedupeDrafts(drafts: List<Draft>): List<Draft> {
        val result = mutableListOf<Draft>()
        for (draft in drafts.sortedByDescending { it.confidence }) {
            val duplicate = result.any {
                it.groupKey == draft.groupKey &&
                    TextSimilarity.jaccard(it.title, draft.title) >= 0.7
            }
            if (!duplicate) result += draft
        }
        return result
    }
}
