package com.quntodo.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.quntodo.app.QunTodoApp
import com.quntodo.app.data.db.entity.RawMessageEntity
import com.quntodo.app.data.db.entity.TodoEntity
import com.quntodo.app.data.repo.TodoRepository
import com.quntodo.app.ui.pickDateTime
import com.quntodo.app.util.TimeFormat
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScreen(
    todo: TodoEntity?,
    isNew: Boolean,
    onBack: () -> Unit,
    onSave: (TodoRepository.TodoForm) -> Unit,
    onDelete: (TodoEntity) -> Unit
) {
    if (!isNew && todo == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val context = LocalContext.current
    val app = context.applicationContext as QunTodoApp
    val scope = rememberCoroutineScope()

    var title by remember(todo?.id) { mutableStateOf(todo?.title.orEmpty()) }
    var body by remember(todo?.id) { mutableStateOf(todo?.body.orEmpty()) }
    var groupName by remember(todo?.id) { mutableStateOf(todo?.groupName.orEmpty()) }
    var deadlineAt by remember(todo?.id) { mutableStateOf(todo?.deadlineAt) }
    var deadlineHasTime by remember(todo?.id) { mutableStateOf(todo?.deadlineHasTime ?: false) }
    var pinned by remember(todo?.id) { mutableStateOf(todo?.pinned ?: false) }
    var imagePaths by remember(todo?.id) { mutableStateOf(todo?.imagePathList.orEmpty()) }
    var sources by remember(todo?.id) { mutableStateOf<List<RawMessageEntity>>(emptyList()) }
    var deadlineMenu by remember { mutableStateOf(false) }
    var previewImagePath by remember { mutableStateOf<String?>(null) }

    val pickImages = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(5)
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                val added = uris.mapNotNull { app.container.imageStore.copyFrom(it) }
                imagePaths = imagePaths + added
            }
        }
    }

    LaunchedEffect(todo?.id) {
        if (todo != null && todo.sourceIdList.isNotEmpty()) {
            sources = app.container.repository.getRawMessages(todo.sourceIdList)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "新建待办" else "编辑待办") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            onSave(
                                TodoRepository.TodoForm(
                                    title = title,
                                    body = body,
                                    groupName = groupName,
                                    deadlineAt = deadlineAt,
                                    deadlineHasTime = deadlineHasTime,
                                    pinned = pinned,
                                    imagePaths = imagePaths
                                )
                            )
                        },
                        enabled = title.isNotBlank()
                    ) {
                        Text("保存")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("要做的事") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                label = { Text("详细内容") },
                minLines = 4,
                maxLines = 10,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = groupName,
                onValueChange = { groupName = it },
                label = { Text("来自哪个群（可留空）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box {
                        TextButton(onClick = { deadlineMenu = true }) {
                            Icon(
                                Icons.Default.DateRange,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                deadlineAt?.let {
                                    TimeFormat.deadline(it, deadlineHasTime)
                                } ?: "设置截止时间（可选）"
                            )
                        }
                        DropdownMenu(expanded = deadlineMenu, onDismissRequest = { deadlineMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("选择日期和时间") },
                                onClick = {
                                    deadlineMenu = false
                                    pickDateTime(context, deadlineAt, false) { at, has ->
                                        deadlineAt = at
                                        deadlineHasTime = has
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("只选日期") },
                                onClick = {
                                    deadlineMenu = false
                                    pickDateTime(context, deadlineAt, true) { at, has ->
                                        deadlineAt = at
                                        deadlineHasTime = has
                                    }
                                }
                            )
                            if (deadlineAt != null) {
                                DropdownMenuItem(
                                    text = { Text("清除截止时间") },
                                    onClick = {
                                        deadlineMenu = false
                                        deadlineAt = null
                                    }
                                )
                            }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    if (deadlineAt != null) {
                        IconButton(onClick = { deadlineAt = null }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "清除截止时间",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("置顶", modifier = Modifier.weight(1f))
                Switch(checked = pinned, onCheckedChange = { pinned = it })
            }

            Text(
                "图片",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 4.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                imagePaths.forEach { path ->
                    Box {
                        AsyncImage(
                            model = File(path),
                            contentDescription = "图片",
                            modifier = Modifier
                                .size(88.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { previewImagePath = path },
                            contentScale = ContentScale.Crop
                        )
                        IconButton(
                            onClick = { imagePaths = imagePaths - path },
                            modifier = Modifier.align(Alignment.TopEnd)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "移除图片",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .size(88.dp)
                        .clip(RoundedCornerShape(10.dp))
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        IconButton(onClick = {
                            pickImages.launch(
                                PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly
                                )
                            )
                        }) {
                            Icon(Icons.Default.Add, contentDescription = "添加图片")
                        }
                        Text("添加图片", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (imagePaths.isNotEmpty()) {
                Text(
                    "点击图片可放大查看",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            if (sources.isNotEmpty()) {
                Text(
                    "来源消息",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
                sources.forEach { source ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            Text(
                                listOf(
                                    source.sender.ifBlank { "群成员" },
                                    TimeFormat.relative(source.postedAt)
                                ).joinToString(" · "),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                source.content,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(top = 3.dp)
                            )
                        }
                    }
                }
            }

            if (!isNew && todo != null) {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { onDelete(todo) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("删除这条待办")
                }
            }
        }
    }

    previewImagePath?.let { path ->
        Dialog(
            onDismissRequest = { previewImagePath = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable { previewImagePath = null },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = File(path),
                    contentDescription = "查看大图",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    contentScale = ContentScale.Fit
                )
                IconButton(
                    onClick = { previewImagePath = null },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "关闭",
                        tint = Color.White
                    )
                }
            }
        }
    }
}
