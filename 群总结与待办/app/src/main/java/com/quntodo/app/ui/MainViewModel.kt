package com.quntodo.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.quntodo.app.QunTodoApp
import com.quntodo.app.data.db.entity.RawMessageEntity
import com.quntodo.app.data.db.entity.TodoEntity
import com.quntodo.app.data.prefs.AppSettings
import com.quntodo.app.data.repo.TodoRepository
import com.quntodo.app.reminders.ReminderScheduler
import com.quntodo.app.rules.RuleEngine
import com.quntodo.app.ui.nav.Screen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UndoState(val todoId: Long, val text: String)

data class UiState(
    val screen: Screen = Screen.Home,
    val todos: List<TodoEntity> = emptyList(),
    val completedTodos: List<TodoEntity> = emptyList(),
    val inbox: List<RawMessageEntity> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val loading: Boolean = true,
    val searchQuery: String = "",
    val searching: Boolean = false,
    val groupFilter: String? = null,
    val showCompleted: Boolean = false,
    val drafts: List<RuleEngine.Draft> = emptyList(),
    val draftSummary: String = "",
    val draftAiUsed: Boolean = false,
    val draftAiError: String? = null,
    val draftRawIds: List<Long> = emptyList(),
    val summarizing: Boolean = false,
    val importingScreenshot: Boolean = false,
    val message: String? = null,
    val undo: UndoState? = null,
    val editing: TodoEntity? = null,
    val editingIsNew: Boolean = false,
    val settingsMessage: String? = null,
    val testingAi: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as QunTodoApp
    private val repository: TodoRepository = app.container.repository
    private val settingsStore = app.container.settingsStore

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private data class Snapshot(
        val active: List<TodoEntity>,
        val done: List<TodoEntity>,
        val inbox: List<RawMessageEntity>,
        val settings: AppSettings
    )

    init {
        viewModelScope.launch {
            combine(
                repository.observeTodos(false),
                repository.observeTodos(true),
                repository.observeInbox(),
                settingsStore.settings
            ) { active, done, inbox, settings ->
                Snapshot(active, done, inbox, settings)
            }.collect { snapshot ->
                _state.update {
                    it.copy(
                        todos = snapshot.active,
                        completedTodos = snapshot.done,
                        inbox = snapshot.inbox,
                        settings = snapshot.settings,
                        loading = false
                    )
                }
            }
        }
    }

    // ---------- 导航 ----------

    fun openScreen(screen: Screen) {
        _state.update { it.copy(screen = screen, searching = false, message = null) }
    }

    fun openInbox() = openScreen(Screen.Inbox)

    fun openSettings() = openScreen(Screen.Settings)

    fun openOnboarding() = openScreen(Screen.Onboarding)

    fun openNewTodo() {
        _state.update {
            it.copy(
                screen = Screen.Edit(null),
                editing = null,
                editingIsNew = true,
                searching = false
            )
        }
    }

    fun openEdit(todoId: Long) {
        viewModelScope.launch {
            val todo = repository.getTodo(todoId)
            _state.update {
                if (todo == null) {
                    it.copy(screen = Screen.Home, message = "这条待办已经不存在了")
                } else {
                    it.copy(
                        screen = Screen.Edit(todoId),
                        editing = todo,
                        editingIsNew = false,
                        searching = false
                    )
                }
            }
        }
    }

    fun back() {
        _state.update { current ->
            val target = when (current.screen) {
                Screen.Preview -> Screen.Inbox
                is Screen.Edit -> Screen.Home
                Screen.Inbox, Screen.Settings, Screen.Onboarding -> Screen.Home
                Screen.Home -> Screen.Home
            }
            current.copy(screen = target, editing = null, editingIsNew = false)
        }
    }

    // ---------- 首页筛选 ----------

    fun setSearchQuery(query: String) = _state.update { it.copy(searchQuery = query) }

    fun toggleSearch() = _state.update {
        it.copy(searching = !it.searching, searchQuery = if (it.searching) "" else it.searchQuery)
    }

    fun selectGroup(group: String?) = _state.update { it.copy(groupFilter = group) }

    fun toggleCompletedView() = _state.update { it.copy(showCompleted = !it.showCompleted) }

    // ---------- 待办操作 ----------

    fun togglePinned(todo: TodoEntity) {
        viewModelScope.launch { repository.setPinned(todo.id, !todo.pinned) }
    }

    fun toggleCompleted(todo: TodoEntity) {
        viewModelScope.launch {
            repository.setCompleted(todo.id, !todo.completed)
            _state.update {
                it.copy(
                    message = if (!todo.completed) "已完成「${todo.title}」" else "已恢复为未完成"
                )
            }
        }
    }

    fun deleteTodo(todo: TodoEntity) {
        viewModelScope.launch {
            repository.deleteTodo(todo.id)
            _state.update {
                it.copy(
                    screen = Screen.Home,
                    editing = null,
                    undo = UndoState(todo.id, "已删除「${todo.title}」")
                )
            }
        }
    }

    fun deleteFromEdit(todo: TodoEntity) = deleteTodo(todo)

    fun undoDelete() {
        val undo = _state.value.undo ?: return
        viewModelScope.launch {
            repository.restoreTodo(undo.todoId)
            _state.update { it.copy(undo = null, message = "已撤销删除") }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    fun consumeUndo() = _state.update { it.copy(undo = null) }

    fun saveTodo(form: TodoRepository.TodoForm) {
        val editingId = (_state.value.screen as? Screen.Edit)?.todoId
        viewModelScope.launch {
            if (editingId == null) {
                repository.createTodo(form)
            } else {
                repository.updateTodo(editingId, form)
            }
            _state.update {
                it.copy(
                    screen = Screen.Home,
                    editing = null,
                    editingIsNew = false,
                    message = if (editingId == null) "已添加待办" else "已保存修改"
                )
            }
        }
    }

    // ---------- 收件箱 ----------

    fun ignoreInboxMessage(id: Long) {
        viewModelScope.launch {
            repository.ignoreInbox(listOf(id))
            _state.update { it.copy(message = "已忽略这条消息") }
        }
    }

    fun ignoreAllInbox() {
        val ids = _state.value.inbox.map { it.id }
        viewModelScope.launch {
            repository.ignoreInbox(ids)
            _state.update { it.copy(message = "已清空待整理") }
        }
    }

    fun summarizeInbox() {
        val ids = _state.value.inbox.map { it.id }
        if (ids.isEmpty()) return
        _state.update { it.copy(summarizing = true) }
        viewModelScope.launch {
            val result = runCatching { repository.buildDrafts(ids) }
            result.fold(
                onSuccess = { draftResult ->
                    _state.update {
                        it.copy(
                            summarizing = false,
                            drafts = draftResult.drafts,
                            draftSummary = draftResult.summary,
                            draftAiUsed = draftResult.aiUsed,
                            draftAiError = draftResult.aiError,
                            draftRawIds = ids,
                            screen = if (draftResult.drafts.isEmpty()) Screen.Inbox else Screen.Preview,
                            message = if (draftResult.drafts.isEmpty()) draftResult.summary else null
                        )
                    }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            summarizing = false,
                            message = "整理失败：${error.message ?: "未知错误"}"
                        )
                    }
                }
            )
        }
    }

    fun importText(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            val id = repository.ingestManualText(text)
            _state.update {
                it.copy(
                    message = if (id != null) "已导入到待整理" else "这段内容刚刚已经导入过了"
                )
            }
        }
    }

    fun importScreenshot(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(importingScreenshot = true) }
            val path = app.container.imageStore.copyFrom(uri)
            if (path == null) {
                _state.update {
                    it.copy(importingScreenshot = false, message = "无法读取这张图片")
                }
                return@launch
            }
            val result = runCatching { repository.ingestScreenshot(path) }
            result.fold(
                onSuccess = { imported ->
                    _state.update {
                        it.copy(
                            importingScreenshot = false,
                            message = when {
                                imported.recognized == 0 -> "没有在截图里识别出文字，已保存图片"
                                imported.inserted == 0 -> "这张截图已经导入过了"
                                else -> "已识别 ${imported.inserted} 段聊天记录，去待整理查看"
                            }
                        )
                    }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            importingScreenshot = false,
                            message = "识别失败：${error.message ?: "未知错误"}"
                        )
                    }
                }
            )
        }
    }

    // ---------- 结果预览 ----------

    fun updateDraftTitle(index: Int, value: String) {
        _state.update { current ->
            if (index !in current.drafts.indices) return@update current
            val list = current.drafts.toMutableList()
            list[index] = list[index].copy(title = value)
            current.copy(drafts = list)
        }
    }

    fun updateDraftBody(index: Int, value: String) {
        _state.update { current ->
            if (index !in current.drafts.indices) return@update current
            val list = current.drafts.toMutableList()
            list[index] = list[index].copy(body = value)
            current.copy(drafts = list)
        }
    }

    fun updateDraftDeadline(index: Int, at: Long?, hasTime: Boolean) {
        _state.update { current ->
            if (index !in current.drafts.indices) return@update current
            val list = current.drafts.toMutableList()
            list[index] = list[index].copy(deadlineAt = at, deadlineHasTime = hasTime)
            current.copy(drafts = list)
        }
    }

    fun removeDraft(index: Int) {
        _state.update { current ->
            if (index !in current.drafts.indices) return@update current
            val list = current.drafts.toMutableList().apply { removeAt(index) }
            current.copy(drafts = list)
        }
    }

    fun saveDrafts() {
        val current = _state.value
        val cleaned = current.drafts.filter { it.title.isNotBlank() }
        if (cleaned.isEmpty()) {
            _state.update { it.copy(drafts = emptyList(), screen = Screen.Inbox) }
            return
        }
        viewModelScope.launch {
            val count = repository.confirmDrafts(
                drafts = cleaned,
                rawIds = current.draftRawIds,
                summary = current.draftSummary,
                aiUsed = current.draftAiUsed
            )
            _state.update {
                it.copy(
                    screen = Screen.Home,
                    drafts = emptyList(),
                    draftRawIds = emptyList(),
                    message = "已保存 $count 条待办"
                )
            }
        }
    }

    fun discardDrafts() {
        _state.update {
            it.copy(screen = Screen.Inbox, drafts = emptyList(), draftRawIds = emptyList())
        }
    }

    // ---------- 设置 ----------

    fun setAiEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setAiEnabled(enabled) }
    }

    fun saveAiConfig(baseUrl: String, model: String, key: String) {
        viewModelScope.launch {
            settingsStore.setAiConfig(baseUrl, model, key)
            _state.update { it.copy(settingsMessage = "AI 设置已保存") }
        }
    }

    fun clearApiKey() {
        viewModelScope.launch {
            settingsStore.clearApiKey()
            _state.update { it.copy(settingsMessage = "已清除 API Key") }
        }
    }

    fun testAi(baseUrl: String, model: String, key: String) {
        val effectiveKey = key.ifBlank { _state.value.settings.apiKey }
        if (effectiveKey.isBlank()) {
            _state.update { it.copy(settingsMessage = "请先填写 API Key") }
            return
        }
        _state.update { it.copy(testingAi = true, settingsMessage = null) }
        viewModelScope.launch {
            val result = repository.testAi(effectiveKey, baseUrl, model)
            _state.update {
                it.copy(
                    testingAi = false,
                    settingsMessage = result.getOrNull()
                        ?: result.exceptionOrNull()?.message
                        ?: "测试失败"
                )
            }
        }
    }

    fun setRemindersEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setRemindersEnabled(enabled)
            _state.update {
                it.copy(
                    settingsMessage = if (enabled) {
                        "提醒已开启，会按截止时间安排闹钟"
                    } else {
                        "提醒已关闭，所有已安排的闹钟已取消"
                    }
                )
            }
        }
    }

    fun setReminderLead(minutes: Int) {
        viewModelScope.launch {
            settingsStore.setReminderLead(minutes)
            repository.rescheduleAllReminders()
            _state.update { it.copy(settingsMessage = "提醒时间已更新") }
        }
    }

    fun setCaptureEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setCaptureEnabled(enabled) }
    }

    fun setCapturePrivate(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setCapturePrivate(enabled) }
    }

    fun setGroupAllowlist(groups: Set<String>) {
        viewModelScope.launch { settingsStore.setGroupAllowlist(groups) }
    }

    fun finishOnboarding() {
        viewModelScope.launch {
            settingsStore.setOnboarded(true)
            _state.update { it.copy(screen = Screen.Home, settingsMessage = null) }
        }
    }

    fun reconnectListener() {
        requestListenerRebind(getApplication())
        _state.update { it.copy(settingsMessage = "正在重新连接通知监听…") }
    }

    fun rescheduleReminders() {
        viewModelScope.launch {
            repository.rescheduleAllReminders()
            _state.update { it.copy(settingsMessage = "提醒已重新安排") }
        }
    }

    fun showSettingsMessage(text: String) = _state.update { it.copy(settingsMessage = text) }

    fun clearSettingsMessage() = _state.update { it.copy(settingsMessage = null) }

    fun simulateIncomingMessage() {
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            val inserted = repository.ingestIncoming(
                listOf(
                    TodoRepository.IncomingMessage(
                        packageName = "com.quntodo.test",
                        groupName = "测试群",
                        sender = "班长",
                        content = "请大家周五前把报名表交到班长那里，记得带学生证。",
                        postedAt = now,
                        notificationKey = "test-$now"
                    )
                )
            )
            _state.update {
                it.copy(
                    message = if (inserted > 0) "已模拟一条群消息，去待整理看看" else "模拟消息重复了"
                )
            }
        }
    }

    fun defaultReminderLead(): Int = _state.value.settings.reminderLeadMinutes

    fun previewReminderAt(deadlineAt: Long?, hasTime: Boolean): Long? =
        ReminderScheduler.computeReminderAt(
            deadlineAt,
            hasTime,
            _state.value.settings.reminderLeadMinutes,
            _state.value.settings.dateOnlyReminderHour
        )
}
