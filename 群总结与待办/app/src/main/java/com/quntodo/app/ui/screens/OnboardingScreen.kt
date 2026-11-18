package com.quntodo.app.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quntodo.app.ui.MainViewModel
import com.quntodo.app.ui.UiState
import com.quntodo.app.ui.canScheduleExactAlarms
import com.quntodo.app.ui.hasPostNotifications
import com.quntodo.app.ui.isIgnoringBatteryOptimizations
import com.quntodo.app.ui.isNotificationListenerEnabled
import com.quntodo.app.ui.openBatterySettings
import com.quntodo.app.ui.openExactAlarmSettings
import com.quntodo.app.ui.openListenerSettings
import com.quntodo.app.ui.rememberResumeTick

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(state: UiState, viewModel: MainViewModel) {
    val context = LocalContext.current
    val tick = rememberResumeTick()
    val listenerEnabled = remember(tick) { isNotificationListenerEnabled(context) }
    val notificationsOk = remember(tick) { hasPostNotifications(context) }
    val exactAlarm = remember(tick) { canScheduleExactAlarms(context) }
    val batteryOk = remember(tick) { isIgnoringBatteryOptimizations(context) }
    val alreadyOnboarded = state.settings.onboarded

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    Scaffold(
        topBar = {
            if (alreadyOnboarded) {
                TopAppBar(
                    title = { Text("使用引导") },
                    navigationIcon = {
                        IconButton(onClick = viewModel::back) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "BH List",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                "把 QQ 群里的通知，自动收成一张张待办卡片。\n先花一分钟开启下面几项权限。",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))

            OnboardStep(
                number = 1,
                title = "开启通知使用权",
                done = listenerEnabled,
                description = "这是自动抓取群消息的前提。点击后找到「BH List」，允许读取通知。",
                actionLabel = if (listenerEnabled) "已开启" else "去开启",
                actionEnabled = !listenerEnabled,
                onAction = { openListenerSettings(context) }
            )

            OnboardStep(
                number = 2,
                title = "允许发送通知",
                done = notificationsOk,
                description = "截止时间到了需要用通知提醒你。",
                actionLabel = if (notificationsOk) "已允许" else "允许通知",
                actionEnabled = !notificationsOk,
                onAction = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            )

            OnboardStep(
                number = 3,
                title = "允许精确提醒",
                done = exactAlarm && batteryOk,
                description = "否则手机省电策略可能让提醒晚到或不到。",
                actionLabel = if (exactAlarm && batteryOk) null else "去设置",
                actionEnabled = !(exactAlarm && batteryOk),
                onAction = {
                    if (!exactAlarm) openExactAlarmSettings(context) else openBatterySettings(context)
                }
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { openExactAlarmSettings(context) }) {
                        Text(if (exactAlarm) "闹钟已允许" else "允许精确闹钟", fontSize = 12.sp)
                    }
                    OutlinedButton(onClick = { openBatterySettings(context) }) {
                        Text(if (batteryOk) "已忽略电池优化" else "忽略电池优化", fontSize = 12.sp)
                    }
                }
            }

            OnboardStep(
                number = 4,
                title = "在 QQ / 微信里打开消息详情",
                done = false,
                description = "QQ：设置 → 消息通知 → 打开「显示消息详情」。微信：我 → 设置 → 消息通知 → 打开「通知显示消息详情」。不打开的话，通知里只有「你收到一条消息」，软件拿不到内容。",
                actionLabel = null,
                actionEnabled = false,
                onAction = {}
            )

            OnboardStep(
                number = 5,
                title = "两点需要知道",
                done = false,
                description = "① 群里设了「免打扰」，QQ 不会弹通知，也就抓不到。\n② 通知里只有文字，图片需要从 QQ 分享或相册导入。",
                actionLabel = null,
                actionEnabled = false,
                onAction = {}
            )

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    if (alreadyOnboarded) viewModel.back() else viewModel.finishOnboarding()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text(if (alreadyOnboarded) "返回" else "开始使用")
            }
            TextButton(
                onClick = {
                    if (alreadyOnboarded) viewModel.back() else viewModel.finishOnboarding()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "没开通知使用权也能用，消息可以手动导入",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun OnboardStep(
    number: Int,
    title: String,
    done: Boolean,
    description: String,
    actionLabel: String?,
    actionEnabled: Boolean,
    onAction: () -> Unit,
    extra: (@Composable () -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Surface(
                color = if (done) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                },
                shape = CircleShape,
                modifier = Modifier.size(26.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Text(
                        if (done) "✓" else number.toString(),
                        fontSize = 12.sp,
                        color = if (done) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        }
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    description,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 3.dp)
                )
                if (extra != null) {
                    Spacer(Modifier.height(6.dp))
                    extra()
                }
                if (actionLabel != null) {
                    TextButton(
                        onClick = onAction,
                        enabled = actionEnabled,
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Text(actionLabel, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
