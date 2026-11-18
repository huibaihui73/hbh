package com.quntodo.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quntodo.app.R
import com.quntodo.app.data.db.entity.TodoEntity
import com.quntodo.app.ui.UiState
import com.quntodo.app.ui.components.EmptyState
import com.quntodo.app.ui.components.InboxBanner
import com.quntodo.app.ui.components.TodoCard
import com.quntodo.app.ui.openListenerDetailSettings
import com.quntodo.app.ui.openListenerSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: UiState,
    listenerEnabled: Boolean,
    listenerConnected: Boolean,
    onReconnectListener: () -> Unit,
    onOpenInbox: () -> Unit,
    onOpenSettings: () -> Unit,
    onImportScreenshot: (Uri) -> Unit,
    onNewTodo: () -> Unit,
    onEditTodo: (Long) -> Unit,
    onToggleComplete: (TodoEntity) -> Unit,
    onTogglePin: (TodoEntity) -> Unit,
    onDelete: (TodoEntity) -> Unit,
    onSearchChange: (String) -> Unit,
    onToggleSearch: () -> Unit,
    onSelectGroup: (String?) -> Unit,
    onToggleCompleted: () -> Unit,
    onUndo: () -> Unit,
    onConsumeUndo: () -> Unit,
    onConsumeMessage: () -> Unit
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val searchFocus = remember { FocusRequester() }
    val pickScreenshot = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) onImportScreenshot(uri)
    }

    LaunchedEffect(state.undo) {
        val undo = state.undo ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(undo.text, actionLabel = "撤销")
        if (result == SnackbarResult.ActionPerformed) onUndo() else onConsumeUndo()
    }
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onConsumeMessage()
    }

    val groups = remember(state.todos, state.completedTodos) {
        (state.todos + state.completedTodos)
            .mapNotNull { it.groupName }
            .distinct()
            .sorted()
    }

    val visible = remember(
        state.todos,
        state.completedTodos,
        state.showCompleted,
        state.groupFilter,
        state.searchQuery
    ) {
        val base = if (state.showCompleted) state.completedTodos else state.todos
        base.filter { todo ->
            (state.groupFilter == null || todo.groupName == state.groupFilter) &&
                (state.searchQuery.isBlank() ||
                    todo.title.contains(state.searchQuery, ignoreCase = true) ||
                    todo.body.contains(state.searchQuery, ignoreCase = true) ||
                    (todo.groupName ?: "").contains(state.searchQuery, ignoreCase = true))
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("BH List", style = MaterialTheme.typography.headlineSmall) },
                    actions = {
                        IconButton(
                            onClick = {
                                pickScreenshot.launch(
                                    PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageOnly
                                    )
                                )
                            },
                            enabled = !state.importingScreenshot
                        ) {
                            if (state.importingScreenshot) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    painterResource(R.drawable.ic_image),
                                    contentDescription = "导入聊天截图"
                                )
                            }
                        }
                        IconButton(onClick = onToggleSearch) {
                            Icon(Icons.Default.Search, contentDescription = "搜索")
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Default.Settings, contentDescription = "设置")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
                if (state.searching) {
                    LaunchedEffect(Unit) {
                        searchFocus.requestFocus()
                    }
                    OutlinedTextField(
                        value = state.searchQuery,
                        onValueChange = onSearchChange,
                        placeholder = { Text("搜索标题、内容或群名") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (state.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchChange("") }) {
                                    Icon(Icons.Default.Close, contentDescription = "清除")
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .focusRequester(searchFocus)
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNewTodo,
                modifier = Modifier.navigationBarsPadding(),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "新建待办")
            }
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (!listenerEnabled || !listenerConnected) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Row(
                        Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (!listenerEnabled) {
                                "通知抓取未开启，群消息不会自动进来"
                            } else {
                                "通知监听未连接，请到系统设置里重新授权"
                            },
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = {
                                if (!listenerEnabled) {
                                    openListenerSettings(context)
                                } else {
                                    onReconnectListener()
                                    openListenerDetailSettings(context)
                                }
                            }
                        ) {
                            Text(
                                if (!listenerEnabled) "去开启" else "去修复",
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            if (state.inbox.isNotEmpty() && !state.showCompleted) {
                InboxBanner(state.inbox.size, onOpenInbox)
            }

            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = state.groupFilter == null && !state.showCompleted,
                    onClick = {
                        onSelectGroup(null)
                        if (state.showCompleted) onToggleCompleted()
                    },
                    label = { Text("全部") }
                )
                FilterChip(
                    selected = state.showCompleted,
                    onClick = onToggleCompleted,
                    label = { Text("已完成") }
                )
                groups.forEach { group ->
                    FilterChip(
                        selected = state.groupFilter == group,
                        onClick = { onSelectGroup(if (state.groupFilter == group) null else group) },
                        label = { Text(group, maxLines = 1) }
                    )
                }
            }

            if (visible.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.Check,
                    title = when {
                        state.showCompleted -> "还没有已完成的待办"
                        state.searchQuery.isNotBlank() -> "没有找到匹配的待办"
                        state.todos.isEmpty() -> "还没有待办"
                        else -> "这个群暂时没有待办"
                    },
                    subtitle = when {
                        state.searching -> "换个关键词试试"
                        state.inbox.isEmpty() -> "群消息会先进「待整理」，点一下就能变成待办"
                        else -> "上面有 ${state.inbox.size} 条消息等你整理"
                    }
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(visible, key = { it.id }) { todo ->
                        TodoCard(
                            todo = todo,
                            onClick = { onEditTodo(todo.id) },
                            onToggleComplete = { onToggleComplete(todo) },
                            onTogglePin = { onTogglePin(todo) },
                            onDelete = { onDelete(todo) }
                        )
                    }
                }
            }
        }
    }
}
