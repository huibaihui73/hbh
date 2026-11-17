package com.quntodo.app.data.repo

import android.content.Context
import androidx.room.withTransaction
import com.quntodo.app.ai.AiClient
import com.quntodo.app.data.db.AppDatabase
import com.quntodo.app.data.db.entity.BatchEntity
import com.quntodo.app.data.db.entity.RawMessageEntity
import com.quntodo.app.data.db.entity.TodoEntity
import com.quntodo.app.data.media.ImageStore
import com.quntodo.app.data.ocr.ScreenshotOcr
import com.quntodo.app.data.prefs.AppSettings
import com.quntodo.app.data.prefs.SettingsStore
import com.quntodo.app.reminders.NotificationHelper
import com.quntodo.app.reminders.ReminderScheduler
import com.quntodo.app.rules.RuleEngine
import com.quntodo.app.rules.SummaryBuilder
import com.quntodo.app.rules.TextNormalizer
import com.quntodo.app.util.RuntimeState
import com.quntodo.app.util.TextSimilarity
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.security.MessageDigest
import kotlin.random.Random

class TodoRepository(
    private val context: Context,
    private val database: AppDatabase,
    private val settingsStore: SettingsStore,
    private val imageStore: ImageStore,
    private val ruleEngine: RuleEngine,
    private val aiClient: AiClient,
    private val ocr: ScreenshotOcr,
    private val reminderScheduler: ReminderScheduler
) {

    private val todoDao get() = database.todoDao()
    private val rawDao get() = database.rawMessageDao()
    private val batchDao get() = database.batchDao()

    data class IncomingMessage(
        val packageName: String = "",
        val groupName: String,
        val sender: String = "",
        val content: String,
        val postedAt: Long,
        val notificationKey: String = "",
        val source: String = RawMessageEntity.SOURCE_NOTIFICATION,
        val imagePath: String? = null
    )

    data class TodoForm(
        val title: String,
        val body: String = "",
        val groupName: String = "",
        val deadlineAt: Long? = null,
        val deadlineHasTime: Boolean = false,
        val pinned: Boolean = false,
        val imagePaths: List<String> = emptyList()
    )

    data class DraftResult(
        val drafts: List<RuleEngine.Draft>,
        val summary: String,
        val aiUsed: Boolean,
        val aiError: String?
    )

    data class Diagnostics(
        val activeTodos: Int,
        val pendingTodos: Int,
        val inboxMessages: Int
    )

    data class ScreenshotImportResult(val recognized: Int, val inserted: Int)

    fun observeTodos(completed: Boolean): Flow<List<TodoEntity>> =
        todoDao.observeByCompletion(completed)

    fun observeInbox(): Flow<List<RawMessageEntity>> = rawDao.observeUnconsumed()

    fun observePendingReview(): Flow<List<TodoEntity>> = todoDao.observePendingReview()

    suspend fun activeTodos(): List<TodoEntity> = todoDao.getActiveOnce()

    suspend fun ingestIncoming(
        messages: List<IncomingMessage>,
        settings: AppSettings? = null
    ): Int {
        val effective = settings ?: settingsStore.current()
        var inserted = 0
        for (message in messages) {
            if (message.content.isBlank() && message.imagePath == null) continue
            if (effective.ignoreKeywords.any {
                    it.isNotBlank() && message.content.contains(it, ignoreCase = true)
                }
            ) continue
            if (insertIfNew(message) != null) inserted++
        }
        return inserted
    }

    suspend fun ingestManualText(
        text: String,
        groupName: String = "手动导入",
        imagePath: String? = null,
        source: String = RawMessageEntity.SOURCE_PASTE
    ): Long? {
        val content = text
            .replace("​", "")
            .trim()
            .ifBlank { if (imagePath != null) "[图片]" else "" }
        if (content.isBlank()) return null
        val now = System.currentTimeMillis()
        return insertIfNew(
            IncomingMessage(
                packageName = "",
                groupName = groupName.ifBlank { "手动导入" },
                sender = "",
                content = content,
                postedAt = now,
                source = source,
                imagePath = imagePath
            )
        )
    }

    suspend fun ingestScreenshot(
        imagePath: String,
        groupName: String = "截图导入"
    ): ScreenshotImportResult {
        if (imagePath.isBlank()) return ScreenshotImportResult(0, 0)
        val blocks = runCatching { ocr.recognizeBlocks(File(imagePath)) }
            .getOrElse { emptyList() }
        val now = System.currentTimeMillis()

        if (blocks.isEmpty()) {
            insertIfNew(
                IncomingMessage(
                    groupName = groupName,
                    content = "[图片，未识别到文字]",
                    postedAt = now,
                    source = RawMessageEntity.SOURCE_IMAGE,
                    imagePath = imagePath
                )
            )
            return ScreenshotImportResult(0, 0)
        }

        var inserted = 0
        blocks.forEachIndexed { index, block ->
            val id = insertIfNew(
                IncomingMessage(
                    groupName = groupName,
                    content = block,
                    postedAt = now,
                    source = RawMessageEntity.SOURCE_IMAGE,
                    imagePath = if (index == 0) imagePath else null
                )
            )
            if (id != null) inserted++
        }
        return ScreenshotImportResult(blocks.size, inserted)
    }

    private suspend fun insertIfNew(message: IncomingMessage): Long? {
        val groupKey = if (message.packageName.isBlank()) {
            "manual|${message.groupName}"
        } else {
            "${message.packageName}|${message.groupName}"
        }
        val normalized = TextNormalizer.normalize(message.content)
        val hash = sha256(groupKey + "\u0000" + normalized)
        val from = message.postedAt - 10 * 60_000L
        val to = message.postedAt + 10 * 60_000L

        if (rawDao.countSimilar(groupKey, hash, from, to) > 0) return null

        if (message.notificationKey.isNotBlank()) {
            val latest = rawDao.latestByNotificationKey(message.notificationKey)
            if (latest != null) {
                if (latest.content == message.content) return null
                if (TextSimilarity.containsAll(latest.content, message.content)) return null
                if (TextSimilarity.containsAll(message.content, latest.content)) {
                    rawDao.markIgnored(latest.id)
                }
            }
        }

        val entity = RawMessageEntity(
            packageName = message.packageName,
            groupKey = groupKey,
            groupName = message.groupName,
            sender = message.sender,
            content = message.content.take(8000),
            postedAt = message.postedAt,
            capturedAt = System.currentTimeMillis(),
            source = message.source,
            notificationKey = message.notificationKey,
            contentHash = hash,
            imagePath = message.imagePath
        )
        return rawDao.insert(entity)
    }

    suspend fun ignoreInbox(ids: List<Long>) {
        if (ids.isNotEmpty()) rawDao.markIgnoredAll(ids)
    }

    suspend fun buildDrafts(rawIds: List<Long>): DraftResult {
        val settings = settingsStore.current()
        val rows = rawDao.getByIds(rawIds).filter { it.consumedAt == null && !it.ignored }
        if (rows.isEmpty()) {
            return DraftResult(emptyList(), "没有可整理的消息。", false, null)
        }

        val grouped = rows.groupBy { it.groupKey }
        val ruleAll = ruleEngine.extract(rows.map { it.toSourceMessage() })

        val finalDrafts = mutableListOf<RuleEngine.Draft>()
        var aiUsed = false
        var aiError: String? = null

        if (settings.aiReady) {
            for ((groupKey, groupRows) in grouped) {
                val groupName = groupRows.first().groupName
                val sources = groupRows.map { it.toSourceMessage() }
                val result = aiClient.summarize(
                    apiKey = settings.apiKey,
                    baseUrl = settings.apiBaseUrl,
                    model = settings.model,
                    groupName = groupName,
                    messages = sources
                )
                result.fold(
                    onSuccess = { summary ->
                        aiUsed = true
                        finalDrafts += summary.drafts.map { draft ->
                            RuleEngine.Draft(
                                title = draft.title,
                                body = draft.body,
                                groupKey = groupKey,
                                groupName = groupName,
                                deadlineAt = draft.deadlineAt,
                                deadlineHasTime = draft.deadlineHasTime,
                                sourceRawIds = draft.sourceIds.ifEmpty { groupRows.map { it.id } },
                                confidence = draft.confidence,
                                source = TodoEntity.SOURCE_AI
                            )
                        }
                    },
                    onFailure = { error ->
                        aiError = error.message
                        RuntimeState.aiLastError = error.message
                        finalDrafts += ruleAll.filter { it.groupKey == groupKey }
                    }
                )
            }
        } else {
            finalDrafts += ruleAll
        }

        val deduped = mutableListOf<RuleEngine.Draft>()
        for (draft in finalDrafts) {
            val groupKey = draft.groupKey
            if (groupKey != null) {
                val existing = todoDao.getActiveByGroup(groupKey)
                if (existing.any { TextSimilarity.jaccard(it.title, draft.title) >= 0.75 }) continue
            }
            if (deduped.any {
                    it.groupKey == draft.groupKey &&
                        TextSimilarity.jaccard(it.title, draft.title) >= 0.82
                }
            ) continue
            deduped += draft
        }

        return DraftResult(
            drafts = deduped,
            summary = SummaryBuilder.local(rows.size, deduped),
            aiUsed = aiUsed,
            aiError = aiError
        )
    }

    suspend fun confirmDrafts(
        drafts: List<RuleEngine.Draft>,
        rawIds: List<Long>,
        summary: String,
        aiUsed: Boolean
    ): Int {
        val now = System.currentTimeMillis()
        val settings = settingsStore.current()
        val batchId = "b$now-${Random.nextInt(1000, 9999)}"
        val scheduled = mutableListOf<TodoEntity>()
        val imageByRawId = rawDao.getByIds(rawIds)
            .associate { it.id to it.imagePath }

        database.withTransaction {
            batchDao.insert(
                BatchEntity(
                    batchId = batchId,
                    groupKey = drafts.firstOrNull()?.groupKey.orEmpty(),
                    groupName = drafts.firstOrNull()?.groupName.orEmpty(),
                    summary = summary,
                    messageCount = rawIds.size,
                    createdAt = now,
                    status = BatchEntity.STATUS_CONFIRMED,
                    aiUsed = aiUsed
                )
            )
            for (draft in drafts) {
                val title = draft.title.trim()
                if (title.isBlank()) continue
                val reminderAt = reminderFor(
                    draft.deadlineAt, draft.deadlineHasTime, settings, now
                )
                val images = draft.sourceRawIds
                    .mapNotNull { imageByRawId[it] }
                    .distinct()
                val entity = TodoEntity(
                    title = title,
                    body = draft.body.trim(),
                    groupKey = draft.groupKey,
                    groupName = draft.groupName,
                    deadlineAt = draft.deadlineAt,
                    deadlineHasTime = draft.deadlineHasTime,
                    reminderAt = reminderAt,
                    imagePaths = images.joinToString("\n"),
                    createdAt = now,
                    updatedAt = now,
                    reviewState = TodoEntity.STATE_CONFIRMED,
                    source = draft.source,
                    batchId = batchId,
                    sourceRawIds = draft.sourceRawIds.joinToString(","),
                    confidence = draft.confidence
                )
                val id = todoDao.insert(entity)
                scheduled += entity.copy(id = id)
            }
            rawDao.markConsumed(rawIds, batchId, now)
        }

        scheduled.forEach { reminderScheduler.schedule(it) }
        return scheduled.size
    }

    suspend fun createTodo(form: TodoForm): Long {
        val settings = settingsStore.current()
        val now = System.currentTimeMillis()
        val groupName = form.groupName.trim().takeIf { it.isNotBlank() }
        val reminderAt = reminderFor(
            form.deadlineAt, form.deadlineHasTime, settings, now
        )
        val entity = TodoEntity(
            title = form.title.trim(),
            body = form.body.trim(),
            groupKey = groupName?.let { "manual|$it" },
            groupName = groupName,
            deadlineAt = form.deadlineAt,
            deadlineHasTime = form.deadlineHasTime,
            reminderAt = reminderAt,
            pinned = form.pinned,
            createdAt = now,
            updatedAt = now,
            reviewState = TodoEntity.STATE_CONFIRMED,
            source = TodoEntity.SOURCE_MANUAL,
            imagePaths = form.imagePaths.joinToString("\n")
        )
        val id = todoDao.insert(entity)
        reminderScheduler.schedule(entity.copy(id = id))
        return id
    }

    suspend fun updateTodo(id: Long, form: TodoForm) {
        val existing = todoDao.getById(id) ?: return
        val settings = settingsStore.current()
        val now = System.currentTimeMillis()
        val groupName = form.groupName.trim().takeIf { it.isNotBlank() }
        val removedImages = existing.imagePathList - form.imagePaths.toSet()
        val reminderAt = reminderFor(
            form.deadlineAt, form.deadlineHasTime, settings, now
        )
        val updated = existing.copy(
            title = form.title.trim(),
            body = form.body.trim(),
            groupKey = groupName?.let { "manual|$it" } ?: existing.groupKey,
            groupName = groupName ?: existing.groupName,
            deadlineAt = form.deadlineAt,
            deadlineHasTime = form.deadlineHasTime,
            reminderAt = reminderAt,
            pinned = form.pinned,
            updatedAt = now,
            imagePaths = form.imagePaths.joinToString("\n")
        )
        todoDao.update(updated)
        reminderScheduler.schedule(updated)
        imageStore.deletePaths(removedImages)
    }

    suspend fun setPinned(id: Long, pinned: Boolean) {
        todoDao.setPinned(id, pinned, System.currentTimeMillis())
    }

    suspend fun setCompleted(id: Long, completed: Boolean) {
        val todo = todoDao.getById(id) ?: return
        val now = System.currentTimeMillis()
        todoDao.setCompleted(id, completed, if (completed) now else null, now)
        if (completed) {
            reminderScheduler.cancel(id)
            NotificationHelper.cancel(context, id)
        } else {
            reminderScheduler.schedule(todo.copy(completed = false, completedAt = null))
        }
    }

    suspend fun snooze(id: Long, minutes: Int) {
        val todo = todoDao.getById(id) ?: return
        val now = System.currentTimeMillis()
        val at = now + minutes * 60_000L
        todoDao.setReminderAt(id, at, now)
        reminderScheduler.schedule(todo.copy(reminderAt = at))
    }

    suspend fun deleteTodo(id: Long) {
        todoDao.softDelete(id, System.currentTimeMillis())
        reminderScheduler.cancel(id)
        NotificationHelper.cancel(context, id)
    }

    suspend fun restoreTodo(id: Long) {
        todoDao.restore(id, System.currentTimeMillis())
        todoDao.getById(id)?.let { reminderScheduler.schedule(it) }
    }

    suspend fun setRemindersEnabled(enabled: Boolean) {
        settingsStore.setRemindersEnabled(enabled)
        if (enabled) {
            rescheduleAllReminders()
            return
        }
        val now = System.currentTimeMillis()
        for (todo in todoDao.getActiveOnce()) {
            reminderScheduler.cancel(todo.id)
            NotificationHelper.cancel(context, todo.id)
            if (todo.reminderAt != null) {
                todoDao.setReminderAt(todo.id, null, now)
            }
        }
    }

    suspend fun rescheduleAllReminders() {
        val settings = settingsStore.current()
        val todos = todoDao.getActiveOnce()
        for (todo in todos) {
            val reminderAt = reminderFor(
                todo.deadlineAt, todo.deadlineHasTime, settings
            )
            if (reminderAt != todo.reminderAt) {
                todoDao.setReminderAt(todo.id, reminderAt, System.currentTimeMillis())
            }
            reminderScheduler.schedule(todo.copy(reminderAt = reminderAt))
        }
    }

    suspend fun onAppStart() {
        val now = System.currentTimeMillis()
        runCatching { todoDao.purgeDeleted(now - 7L * 24 * 3600_000) }
        runCatching { rawDao.purgeOldConsumed(now - 60L * 24 * 3600_000) }
        runCatching { batchDao.purgeOld(now - 180L * 24 * 3600_000) }
        rescheduleAllReminders()
    }

    suspend fun diagnostics(): Diagnostics = Diagnostics(
        activeTodos = todoDao.countActive(),
        pendingTodos = todoDao.countPending(),
        inboxMessages = rawDao.countUnconsumed()
    )

    suspend fun getTodo(id: Long): TodoEntity? = todoDao.getById(id)

    suspend fun getRawMessages(ids: List<Long>): List<RawMessageEntity> =
        if (ids.isEmpty()) emptyList() else rawDao.getByIds(ids)

    suspend fun testAi(apiKey: String, baseUrl: String, model: String): Result<String> =
        aiClient.testConnection(apiKey, baseUrl, model)

    private fun reminderFor(
        deadlineAt: Long?,
        hasTime: Boolean,
        settings: AppSettings,
        now: Long = System.currentTimeMillis()
    ): Long? =
        if (!settings.remindersEnabled) {
            null
        } else {
            ReminderScheduler.computeReminderAt(
                deadlineAt = deadlineAt,
                deadlineHasTime = hasTime,
                leadMinutes = settings.reminderLeadMinutes,
                dateOnlyHour = settings.dateOnlyReminderHour,
                now = now
            )
        }

    private fun RawMessageEntity.toSourceMessage() = RuleEngine.SourceMessage(
        id = id,
        groupKey = groupKey,
        groupName = groupName,
        sender = sender,
        content = content,
        postedAt = postedAt
    )

    private fun sha256(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
