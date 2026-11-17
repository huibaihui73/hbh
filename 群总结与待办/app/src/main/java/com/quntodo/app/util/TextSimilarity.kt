package com.quntodo.app.util

import java.util.Locale

object TextSimilarity {

    private val noise = Regex("[\\s\\p{Punct}，。！？；：、“”‘’（）【】《》…—·~～]+")

    fun normalize(text: String): String = text.lowercase(Locale.CHINA).replace(noise, "")

    fun bigrams(text: String): Set<String> {
        val normalized = normalize(text)
        if (normalized.length < 2) return if (normalized.isEmpty()) emptySet() else setOf(normalized)
        return (0 until normalized.length - 1).map { normalized.substring(it, it + 2) }.toSet()
    }

    fun jaccard(a: String, b: String): Double {
        val left = bigrams(a)
        val right = bigrams(b)
        if (left.isEmpty() && right.isEmpty()) return 0.0
        val intersection = left.count { it in right }
        val union = left.size + right.size - intersection
        return if (union == 0) 0.0 else intersection.toDouble() / union
    }

    fun containsAll(container: String, part: String): Boolean {
        val c = normalize(container)
        val p = normalize(part)
        return p.isNotEmpty() && c.contains(p)
    }
}
