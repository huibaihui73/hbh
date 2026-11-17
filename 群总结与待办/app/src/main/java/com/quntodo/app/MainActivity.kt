package com.quntodo.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.quntodo.app.ui.MainViewModel
import com.quntodo.app.ui.QunTodoRoot
import com.quntodo.app.ui.theme.QunTodoTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        setContent {
            QunTodoTheme {
                QunTodoRoot(viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val todoId = intent.getLongExtra(EXTRA_TODO_ID, -1L)
        if (todoId > 0L) {
            viewModel.openEdit(todoId)
        }
        if (intent.getBooleanExtra(EXTRA_OPEN_INBOX, false)) {
            viewModel.openInbox()
        }
        intent.removeExtra(EXTRA_TODO_ID)
        intent.removeExtra(EXTRA_OPEN_INBOX)
    }

    companion object {
        const val EXTRA_TODO_ID = "todo_id"
        const val EXTRA_OPEN_INBOX = "open_inbox"
    }
}
