package com.quntodo.app.ai

data class AiDraft(
    val title: String,
    val body: String,
    val deadlineAt: Long?,
    val deadlineHasTime: Boolean,
    val groupName: String?,
    val sourceIds: List<Long>,
    val confidence: Double
)

data class AiSummary(
    val summary: String,
    val drafts: List<AiDraft>
)
