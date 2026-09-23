package com.takeabreak.wearos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.takeabreak.wearos.permission.ReminderDiagnostics
import com.takeabreak.wearos.permission.ReminderMessages
import com.takeabreak.wearos.timer.TimerState
import com.takeabreak.wearos.ui.theme.BreakGreenPrimary
import com.takeabreak.wearos.ui.theme.DarkBackground
import com.takeabreak.wearos.ui.theme.PauseGray
import com.takeabreak.wearos.ui.theme.TextPrimary
import com.takeabreak.wearos.ui.theme.TextSecondary
import com.takeabreak.wearos.ui.theme.WarningRed
import com.takeabreak.wearos.ui.theme.WorkBluePrimary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ReminderStatusScreen(
    state: TimerState,
    permissionEvaluation: ReminderDiagnostics,
    feedback: ActionFeedback?,
    onTestBreakReminder: () -> Unit,
    onTestWorkReminder: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onOpenSystemNotificationSettings: () -> Unit,
    onBack: () -> Unit
) {
    val listState = rememberScalingLazyListState()
    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    ScalingLazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground),
        state = listState,
        contentPadding = PaddingValues(top = 28.dp, bottom = 96.dp, start = 12.dp, end = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Text(
                text = "提醒状态",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            )
        }

        item {
            StatusCard("计时与提醒", ReminderMessages.command(permissionEvaluation.command), permissionEvaluation.command.allowed && permissionEvaluation.command.warnings.isEmpty())
        }
        item {
            val allowed = permissionEvaluation.capabilities.notificationsEnabled == true &&
                permissionEvaluation.capabilities.postNotificationsGranted == true
            StatusCard("系统通知", if (allowed) "已允许" else "未允许或暂不可读取", allowed)
        }
        if (permissionEvaluation.capabilities.notificationsEnabled != true ||
            permissionEvaluation.capabilities.postNotificationsGranted != true) {
            item { FilledTonalButton(onClick = onOpenSystemNotificationSettings) { Text("前往手表通知设置") } }
        }
        item {
            val allowed = permissionEvaluation.capabilities.exactAlarmsAllowed == true
            StatusCard("精确闹钟权限", if (allowed) "已授权" else "未授权或暂不可读取", allowed)
        }
        if (permissionEvaluation.capabilities.exactAlarmsAllowed != true) {
            item { FilledTonalButton(onClick = onOpenExactAlarmSettings) { Text("前往授权精确闹钟") } }
        }
        item {
            val ready = permissionEvaluation.statusChannelReady
            StatusCard("表盘持续活动小图标", if (ready) "状态通知渠道已开启" else "渠道不可用或暂不可读取", ready)
        }
        item {
            StatusCard("休息提醒触感", ReminderMessages.vibration(permissionEvaluation.breakVibration), permissionEvaluation.breakVibration.allowed)
        }
        item {
            StatusCard("工作提醒触感", ReminderMessages.vibration(permissionEvaluation.workVibration), permissionEvaluation.workVibration.allowed)
        }
        item {
            val issue = permissionEvaluation.breakPopup.blocker ?: permissionEvaluation.workPopup.blocker
            StatusCard("弹屏配置", issue?.let(ReminderMessages::describe) ?: "配置满足条件，实际展示由系统决定", issue == null)
        }

        // 最近事件审计
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .background(Color.White.copy(alpha = 0.08f))
                    .padding(8.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "最近调度事件",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                val timeStr = if (state.lastEventTimestampMs > 0L) {
                    timeFormat.format(Date(state.lastEventTimestampMs))
                } else "尚无记录"
                Text(
                    text = "时间: $timeStr",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextPrimary,
                    fontSize = 10.sp
                )
                Text(
                    text = "结果: ${state.lastEventResult.ifEmpty { "初始化完成" }}",
                    style = MaterialTheme.typography.labelSmall,
                    color = WorkBluePrimary,
                    fontSize = 10.sp
                )
                if (state.errorMessage != null) {
                    Text(
                        text = "异常: ${state.errorMessage}",
                        style = MaterialTheme.typography.labelSmall,
                        color = WarningRed,
                        fontSize = 10.sp
                    )
                }
            }
        }

        // Manual previews use the same vibration path as committed phase transitions.
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(
                    onClick = onTestBreakReminder,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .padding(vertical = 2.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BreakGreenPrimary,
                        contentColor = DarkBackground
                    )
                ) {
                    Text(
                        text = "测试休息振动",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = onTestWorkReminder,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .padding(vertical = 2.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = WorkBluePrimary,
                        contentColor = DarkBackground
                    )
                ) {
                    Text(
                        text = "测试工作振动",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        item {
            Text(
                text = "自检与到点提醒使用相同振动节奏；请确认实际触感",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(0.9f)
            )
        }
        if (feedback != null) {
            item {
                Text(
                    text = feedback.message,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextPrimary,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(0.9f)
                        .background(WorkBluePrimary.copy(alpha = 0.2f)).padding(6.dp)
                )
            }
        }

        // 返回按钮
        item {
            Spacer(modifier = Modifier.height(6.dp))
            Button(
                onClick = onBack,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                modifier = Modifier
                    .fillMaxWidth(0.72f)
                    .height(40.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White.copy(alpha = 0.18f),
                    contentColor = TextPrimary
                )
            ) {
                Text(
                    text = "返回",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
private fun StatusCard(
    title: String,
    value: String,
    isOk: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .padding(vertical = 2.dp)
            .background(Color.White.copy(alpha = 0.08f))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                fontSize = 10.sp
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelSmall,
                color = if (isOk) TextPrimary else WarningRed,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp
            )
        }
        Text(
            text = if (isOk) "✓" else "✕",
            color = if (isOk) BreakGreenPrimary else WarningRed,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}
