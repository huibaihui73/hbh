package com.quntodo.app.ui.nav

sealed interface Screen {
    data object Home : Screen
    data object Inbox : Screen
    data object Preview : Screen
    data class Edit(val todoId: Long?) : Screen
    data object Settings : Screen
    data object Onboarding : Screen
}
