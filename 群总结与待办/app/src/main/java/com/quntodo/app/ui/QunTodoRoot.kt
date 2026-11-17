package com.quntodo.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quntodo.app.ui.nav.Screen
import com.quntodo.app.ui.screens.EditScreen
import com.quntodo.app.ui.screens.HomeScreen
import com.quntodo.app.ui.screens.InboxScreen
import com.quntodo.app.ui.screens.OnboardingScreen
import com.quntodo.app.ui.screens.PreviewScreen
import com.quntodo.app.ui.screens.SettingsScreen

@Composable
fun QunTodoRoot(viewModel: MainViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resumeTick = rememberResumeTick()
    val listenerEnabled = remember(resumeTick) { isNotificationListenerEnabled(context) }

    if (!state.loading && !state.settings.onboarded) {
        OnboardingScreen(state = state, viewModel = viewModel)
        return
    }

    BackHandler(enabled = state.screen != Screen.Home) {
        viewModel.back()
    }

    when (state.screen) {
        Screen.Home -> HomeScreen(
            state = state,
            listenerEnabled = listenerEnabled,
            onOpenInbox = viewModel::openInbox,
            onOpenSettings = viewModel::openSettings,
            onImportScreenshot = viewModel::importScreenshot,
            onNewTodo = viewModel::openNewTodo,
            onEditTodo = viewModel::openEdit,
            onToggleComplete = viewModel::toggleCompleted,
            onTogglePin = viewModel::togglePinned,
            onDelete = viewModel::deleteTodo,
            onSearchChange = viewModel::setSearchQuery,
            onToggleSearch = viewModel::toggleSearch,
            onSelectGroup = viewModel::selectGroup,
            onToggleCompleted = viewModel::toggleCompletedView,
            onUndo = viewModel::undoDelete,
            onConsumeUndo = viewModel::consumeUndo,
            onConsumeMessage = viewModel::consumeMessage
        )

        Screen.Inbox -> InboxScreen(
            state = state,
            onBack = viewModel::back,
            onRemove = viewModel::ignoreInboxMessage,
            onSummarize = viewModel::summarizeInbox,
            onImportText = viewModel::importText,
            onImportScreenshot = viewModel::importScreenshot,
            onIgnoreAll = viewModel::ignoreAllInbox,
            onConsumeMessage = viewModel::consumeMessage
        )

        Screen.Preview -> PreviewScreen(
            state = state,
            onBack = viewModel::back,
            onUpdateTitle = viewModel::updateDraftTitle,
            onUpdateBody = viewModel::updateDraftBody,
            onUpdateDeadline = viewModel::updateDraftDeadline,
            onRemoveDraft = viewModel::removeDraft,
            onSave = viewModel::saveDrafts,
            onDiscard = viewModel::discardDrafts
        )

        is Screen.Edit -> EditScreen(
            todo = state.editing,
            isNew = state.editingIsNew,
            onBack = viewModel::back,
            onSave = viewModel::saveTodo,
            onDelete = viewModel::deleteFromEdit
        )

        Screen.Settings -> SettingsScreen(
            state = state,
            viewModel = viewModel,
            onBack = viewModel::back
        )

        Screen.Onboarding -> OnboardingScreen(state = state, viewModel = viewModel)
    }
}
