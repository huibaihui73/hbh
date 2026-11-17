package com.quntodo.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quntodo.app.rules.RuleEngine
import com.quntodo.app.ui.UiState
import com.quntodo.app.ui.components.EmptyState
import com.quntodo.app.ui.components.GroupTag
import com.quntodo.app.ui.pickDateTime
import com.quntodo.app.util.TimeFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewScreen(
    state: UiState,
    onBack: () -> Unit,
    onUpdateTitle: (Int, String) -> Unit,
    onUpdateBody: (Int, String) -> Unit,
    onUpdateDeadline: (Int, Long?, Boolean) -> Unit,
    onRemoveDraft: (Int) -> Unit,
    onSave: () -> Unit,
    onDiscard: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("生成的待办", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = onDiscard) { Text("放弃") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            if (state.drafts.isNotEmpty()) {
                Surface(tonalElevation = 3.dp) {
                    Column(Modifier.navigationBarsPadding()) {
                        Button(
                            onClick = onSave,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                                .height(50.dp)
                        ) {
                            Text("保存 ${state.drafts.size} 条待办")
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        state.draftSummary,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    if (state.draftAiUsed) {
                        Text(
                            "已使用 DeepSeek 整理，保存前可以修改",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    state.draftAiError?.let { error ->
                        Text(
                            "AI 整理失败，已改用本地规则：$error",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            if (state.drafts.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.Check,
                    title = "没有识别到待办",
                    subtitle = "可以回收件箱，把关键消息挑出来再整理一次"
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(state.drafts) { index, draft ->
                        DraftCard(
                            draft = draft,
                            onUpdateTitle = { onUpdateTitle(index, it) },
                            onUpdateBody = { onUpdateBody(index, it) },
                            onUpdateDeadline = { at, hasTime -> onUpdateDeadline(index, at, hasTime) },
                            onRemove = { onRemoveDraft(index) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DraftCard(
    draft: RuleEngine.Draft,
    onUpdateTitle: (String) -> Unit,
    onUpdateBody: (String) -> Unit,
    onUpdateDeadline: (Long?, Boolean) -> Unit,
    onRemove: () -> Unit
) {
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                draft.groupName?.let {
                    GroupTag(it)
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    if (draft.source == "AI") "AI 生成" else "本地规则",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "移除此条",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            OutlinedTextField(
                value = draft.title,
                onValueChange = onUpdateTitle,
                label = { Text("标题") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
            )
            OutlinedTextField(
                value = draft.body,
                onValueChange = onUpdateBody,
                label = { Text("内容") },
                minLines = 2,
                maxLines = 5,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    TextButton(onClick = { menuOpen = true }) {
                        Icon(
                            Icons.Default.DateRange,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            draft.deadlineAt?.let {
                                TimeFormat.deadline(it, draft.deadlineHasTime)
                            } ?: "设置截止时间",
                            fontSize = 13.sp
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("选择日期和时间") },
                            onClick = {
                                menuOpen = false
                                pickDateTime(context, draft.deadlineAt, false) { at, hasTime ->
                                    onUpdateDeadline(at, hasTime)
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("只选日期") },
                            onClick = {
                                menuOpen = false
                                pickDateTime(context, draft.deadlineAt, true) { at, hasTime ->
                                    onUpdateDeadline(at, hasTime)
                                }
                            }
                        )
                        if (draft.deadlineAt != null) {
                            DropdownMenuItem(
                                text = { Text("清除截止时间") },
                                onClick = {
                                    menuOpen = false
                                    onUpdateDeadline(null, false)
                                }
                            )
                        }
                    }
                }
                if (draft.deadlineAt != null) {
                    IconButton(
                        onClick = { onUpdateDeadline(null, false) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "清除截止时间",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
