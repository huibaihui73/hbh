package com.quntodo.app.rules

import java.text.Normalizer

object TextNormalizer {

    // OCR 经常把简体字识别成繁体，这里做一组高频字转换。
    private val traditionalToSimplified = mapOf(
        '內' to '内', '週' to '周', '號' to '号', '時' to '时', '點' to '点',
        '後' to '后', '個' to '个', '這' to '这', '會' to '会', '開' to '开',
        '請' to '请', '學' to '学', '報' to '报', '遲' to '迟', '統' to '统',
        '資' to '资', '訊' to '讯', '鏈' to '链', '結' to '结', '閱' to '阅',
        '務' to '务', '畢' to '毕', '業' to '业', '試' to '试', '線' to '线'
    )

    fun normalize(raw: String): String {
        val nfkc = Normalizer.normalize(raw, Normalizer.Form.NFKC)
        val simplified = buildString(nfkc.length) {
            nfkc.forEach { append(traditionalToSimplified[it] ?: it) }
        }
        return simplified
            .replace("​", "")
            .replace("﻿", "")
            .replace(' ', ' ')
            .lines()
            .joinToString("\n") { it.trim() }
            .trim()
    }

    fun compact(raw: String): String =
        normalize(raw).replace(Regex("\\s+"), " ").trim()
}
