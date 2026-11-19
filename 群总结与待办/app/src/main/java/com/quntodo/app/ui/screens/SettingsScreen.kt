package com.quntodo.app.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quntodo.app.BuildConfig
import com.quntodo.app.data.db.entity.TodoEntity
import com.quntodo.app.reminders.NotificationHelper
import com.quntodo.app.ui.MainViewModel
import com.quntodo.app.ui.UiState
import com.quntodo.app.ui.canScheduleExactAlarms
import com.quntodo.app.ui.hasPostNotifications
import com.quntodo.app.ui.isIgnoringBatteryOptimizations
import com.quntodo.app.ui.isNotificationListenerEnabled
import com.quntodo.app.ui.openBatterySettings
import com.quntodo.app.ui.openExactAlarmSettings
import com.quntodo.app.ui.openListenerDetailSettings
import com.quntodo.app.ui.openListenerSettings
import com.quntodo.app.ui.rememberResumeTick
import com.quntodo.app.util.RuntimeState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: UiState,
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val tick = rememberResumeTick()
    val listenerEnabled = remember(tick) { isNotificationListenerEnabled(context) }
    val listenerConnected by RuntimeState.listenerConnectedFlow.collectAsStateWithLifecycle()
    val exactAlarm = remember(tick) { canScheduleExactAlarms(context) }
    val batteryOk = remember(tick) { isIgnoringBatteryOptimizations(context) }
    val notificationsOk = remember(tick) { hasPostNotifications(context) }

    var keyDraft by remember { mutableStateOf("") }
    var baseDraft by remember(state.settings.apiBaseUrl) { mutableStateOf(state.settings.apiBaseUrl) }
    var modelDraft by remember(state.settings.model) { mutableStateOf(state.settings.model) }
    var showGroupDialog by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.showSettingsMessage(if (granted) "通知权限已开启" else "未获得通知权限，提醒会发不出来")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SettingsCard("AI 总结（DeepSeek）") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("使用 AI 总结群消息")
                            Text(
                                "不开启也能用本地规则整理；开启后需要填 API Key",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = state.settings.aiEnabled,
                            onCheckedChange = viewModel::setAiEnabled
                        )
                    }
                    if (state.settings.aiEnabled) {
                        OutlinedTextField(
                            value = keyDraft,
                            onValueChange = { keyDraft = it },
                            label = { Text("DeepSeek API Key") },
                            placeholder = {
                                Text(
                                    if (state.settings.apiKey.isNotBlank()) {
                                        "已保存，留空则不修改"
                                    } else {
                                        "sk-..."
                                    }
                                )
                            },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp)
                        )
                        OutlinedTextField(
                            value = baseDraft,
                            onValueChange = { baseDraft = it },
                            label = { Text("接口地址") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        )
                        OutlinedTextField(
                            value = modelDraft,
                            onValueChange = { modelDraft = it },
                            label = { Text("模型名") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        )
                        Row(
                            Modifier.padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(onClick = {
                                viewModel.saveAiConfig(baseDraft, modelDraft, keyDraft)
                                keyDraft = ""
                            }) { Text("保存") }
                            OutlinedButton(
                                onClick = { viewModel.testAi(baseDraft, modelDraft, keyDraft) },
                                enabled = !state.testingAi
                            ) {
                                if (state.testingAi) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(Modifier.width(6.dp))
                                }
                                Text("测试连接")
                            }
                            if (state.settings.apiKey.isNotBlank()) {
                                TextButton(onClick = viewModel::clearApiKey) {
                                    Text("清除 Key", fontSize = 12.sp)
                                }
                            }
                        }
                        Text(
                            "Key 只保存在本机（系统密钥库加密）。开启 AI 后，整理时会把群消息发送给 DeepSeek。",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            }

            item {
                SettingsCard("消息抓取") {
                    SwitchRow(
                        label = "自动抓取群消息",
                        checked = state.settings.captureEnabled,
                        onChange = viewModel::setCaptureEnabled
                    )
                    SwitchRow(
                        label = "抓取 QQ 消息",
                        checked = state.settings.captureQq,
                        onChange = viewModel::setCaptureQq
                    )
                    SwitchRow(
                        label = "抓取微信消息",
                        checked = state.settings.captureWechat,
                        onChange = viewModel::setCaptureWechat
                    )
                    Text(
                        "可以分别关闭 QQ 或微信；关闭后对应应用的通知不会进入待整理。",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    HStackStatus(
                        title = "通知使用权",
                        ok = listenerEnabled,
                        actionLabel = if (listenerEnabled) "已开启" else "去开启",
                        actionEnabled = !listenerEnabled
                    ) { openListenerSettings(context) }
                    if (listenerEnabled) {
                        HStackStatus(
                            title = "监听服务",
                            ok = listenerConnected,
                            actionLabel = if (listenerConnected) "已连接" else "去修复",
                            actionEnabled = !listenerConnected
                        ) { openListenerDetailSettings(context) }
                    }
                    SwitchRow(
                        label = "也抓取私聊消息",
                        checked = state.settings.capturePrivateChats,
                        onChange = viewModel::setCapturePrivate
                    )
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("只抓取指定群", fontSize = 14.sp)
                            Text(
                                if (state.settings.groupAllowlist.isEmpty()) {
                                    "当前：抓取全部群"
                                } else {
                                    val names = state.settings.groupAllowlist.sorted()
                                    val preview = names.take(2).joinToString("、")
                                    "当前：$preview" +
                                        if (names.size > 2) " 等 ${names.size} 个群" else ""
                                },
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = { showGroupDialog = true }) { Text("选择") }
                    }
                    Text(
                        "注意：QQ / 微信里需要开启「显示消息详情」，否则通知里没有消息内容；群消息设了免打扰就不会有通知，也抓不到。",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }

            item {
                SettingsCard("提醒") {
                    SwitchRow(
                        label = "截止提醒",
                        checked = state.settings.remindersEnabled,
                        onChange = viewModel::setRemindersEnabled
                    )
                    Text(
                        if (state.settings.remindersEnabled) {
                            "到截止时间前用通知提醒你"
                        } else {
                            "已关闭：不会再安排提醒闹钟（截止时间仍会显示在卡片上）"
                        },
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text("提前多久提醒", fontSize = 14.sp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(0 to "到点", 10 to "10 分钟", 60 to "1 小时", 1440 to "1 天").forEach { (value, label) ->
                            FilterChip(
                                selected = state.settings.reminderLeadMinutes == value,
                                onClick = { viewModel.setReminderLead(value) },
                                enabled = state.settings.remindersEnabled,
                                label = { Text(label) }
                            )
                        }
                    }
                    if (!notificationsOk) {
                        HStackStatus(
                            title = "通知权限",
                            ok = false,
                            actionLabel = "去开启",
                            actionEnabled = true
                        ) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                    }
                    HStackStatus(
                        title = "精确提醒",
                        ok = exactAlarm,
                        actionLabel = if (exactAlarm) "已允许" else "去允许",
                        actionEnabled = !exactAlarm
                    ) { openExactAlarmSettings(context) }
                    HStackStatus(
                        title = "忽略电池优化",
                        ok = batteryOk,
                        actionLabel = if (batteryOk) "已设置" else "去设置",
                        actionEnabled = !batteryOk
                    ) { openBatterySettings(context) }
                    OutlinedButton(
                        onClick = {
                            NotificationHelper.postReminder(
                                context,
                                TodoEntity(
                                    id = 999_999_999L,
                                    title = "测试提醒",
                                    body = "如果你看到这条通知，说明提醒功能正常。",
                                    createdAt = System.currentTimeMillis()
                                )
                            )
                            viewModel.showSettingsMessage(
                                if (notificationsOk) "已发送测试提醒" else "请先开启通知权限"
                            )
                        },
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Text("发一条测试提醒")
                    }
                }
            }

            item {
                SettingsCard("数据") {
                    Text(
                        "待办 ${state.todos.size + state.completedTodos.size} 条 · 待整理 ${state.inbox.size} 条",
                        fontSize = 13.sp
                    )
                    Text(
                        "删除的待办会保留 7 天，之后自动清理。所有数据只存在本机。",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    TextButton(
                        onClick = viewModel::rescheduleReminders,
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Text("重新安排全部提醒", fontSize = 12.sp)
                    }
                }
            }

            item {
                SettingsCard("诊断") {
                    val diagnostic = buildString {
                        appendLine(RuntimeState.snapshot())
                        appendLine("通知使用权：${if (listenerEnabled) "开" else "关"}")
                        appendLine("通知权限：${if (notificationsOk) "开" else "关"}")
                        appendLine("精确闹钟：${if (exactAlarm) "开" else "关"}")
                        appendLine("AI：${if (state.settings.aiReady) "已配置" else "未配置"}")
                        append("版本：${BuildConfig.VERSION_NAME}")
                    }
                    Text(
                        diagnostic,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("BH List 诊断信息", diagnostic))
                            viewModel.showSettingsMessage("诊断信息已复制")
                        }) { Text("复制诊断信息") }
                        if (BuildConfig.DEBUG) {
                            OutlinedButton(onClick = {
                                viewModel.simulateIncomingMessage()
                                viewModel.back()
                            }) { Text("模拟群消息") }
                        }
                    }
                }
            }

            item {
                SettingsCard("关于") {
                    val uriHandler = LocalUriHandler.current
                    Text("BH List v${BuildConfig.VERSION_NAME}", fontSize = 14.sp)
                    Text(
                        "把 QQ / 微信的消息整理成待办卡片。数据只保存在本机；只有开启 AI 时才会把消息内容发送给 DeepSeek。",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Text(
                        "开发者：@huibaihui73",
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Text(
                        "项目地址：https://github.com/huibaihui73/hbh",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .clickable {
                                runCatching {
                                    uriHandler.openUri("https://github.com/huibaihui73/hbh")
                                }
                            }
                    )
                    TextButton(
                        onClick = viewModel::openOnboarding,
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text("查看使用引导", fontSize = 12.sp)
                    }
                }
            }

            item {
                if (state.settingsMessage != null) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            state.settingsMessage,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }
    }

    if (showGroupDialog) {
        val knownGroups = (state.inbox.map { it.groupName } + state.settings.groupAllowlist)
            .distinct()
        var selected by remember(showGroupDialog) {
            mutableStateOf(state.settings.groupAllowlist.toSet())
        }
        var newGroup by remember(showGroupDialog) { mutableStateOf("") }
        val rows = (knownGroups + selected).distinct().sorted()

        AlertDialog(
            onDismissRequest = { showGroupDialog = false },
            title = { Text("只抓取这些群") },
            text = {
                Column(
                    Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        "不勾选任何群 = 抓取全部群。\n可以直接输入群名添加（要和通知里显示的群名一致），也可以在下面勾选已经抓到的群。",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newGroup,
                            onValueChange = { newGroup = it },
                            placeholder = { Text("输入群聊名称", fontSize = 13.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = {
                                val name = newGroup.trim()
                                if (name.isNotEmpty()) selected = selected + name
                                newGroup = ""
                            },
                            enabled = newGroup.isNotBlank()
                        ) { Text("添加") }
                    }
                    if (rows.isNotEmpty()) {
                        Text(
                            "已添加 / 已知群聊",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    rows.forEach { group ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = group in selected,
                                onCheckedChange = { checked ->
                                    selected = if (checked) selected + group else selected - group
                                }
                            )
                            Text(group, fontSize = 14.sp)
                        }
                    }
                    if (selected.isNotEmpty()) {
                        TextButton(onClick = { selected = emptySet() }) {
                            Text("清空，改为抓取全部群", fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setGroupAllowlist(selected)
                    showGroupDialog = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showGroupDialog = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            HorizontalDivider(
                modifier = Modifier.padding(top = 8.dp, bottom = 10.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
            content()
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun HStackStatus(
    title: String,
    ok: Boolean,
    actionLabel: String,
    actionEnabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontSize = 14.sp, modifier = Modifier.weight(1f))
        if (ok) {
            Text(
                actionLabel,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary
            )
        } else {
            TextButton(onClick = onClick, enabled = actionEnabled) {
                Text(actionLabel, fontSize = 12.sp)
            }
        }
    }
}
