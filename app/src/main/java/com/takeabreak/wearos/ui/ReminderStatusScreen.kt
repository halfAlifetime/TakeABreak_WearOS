package com.takeabreak.wearos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import com.takeabreak.wearos.notification.NotificationChannels
import com.takeabreak.wearos.permission.PermissionEvaluation
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
    permissionEvaluation: PermissionEvaluation,
    channelStatuses: List<NotificationChannels.ChannelStatusInfo>,
    actionFeedback: String?,
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
            .background(DarkBackground)
            .padding(horizontal = 10.dp),
        state = listState,
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

        if (actionFeedback != null) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .background(WorkBluePrimary.copy(alpha = 0.2f))
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = actionFeedback,
                        style = MaterialTheme.typography.labelSmall,
                        color = WorkBluePrimary,
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // 核心权限检查项
        item {
            StatusCard(
                title = "系统通知总开关",
                value = if (permissionEvaluation.areNotificationsEnabled) "已允许" else "未允许",
                isOk = permissionEvaluation.areNotificationsEnabled
            )
        }

        if (!permissionEvaluation.areNotificationsEnabled) {
            item {
                FilledTonalButton(
                    onClick = onOpenSystemNotificationSettings,
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .height(34.dp)
                        .padding(vertical = 2.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = WarningRed.copy(alpha = 0.25f),
                        contentColor = TextPrimary
                    )
                ) {
                    Text(
                        text = "前往开启手表通知",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        item {
            StatusCard(
                title = "精确闹钟权限",
                value = if (permissionEvaluation.canScheduleExactAlarms) "已授权 (setAlarmClock)" else "未授权",
                isOk = permissionEvaluation.canScheduleExactAlarms
            )
        }

        if (!permissionEvaluation.canScheduleExactAlarms) {
            item {
                FilledTonalButton(
                    onClick = onOpenExactAlarmSettings,
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .height(34.dp)
                        .padding(vertical = 2.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = WarningRed.copy(alpha = 0.25f),
                        contentColor = TextPrimary
                    )
                ) {
                    Text(
                        text = "前往授权精确闹钟",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 表盘 Ongoing Activity 状态渠道
        item {
            val statusChan = channelStatuses.find { it.channelId == NotificationChannels.CHANNEL_STATUS_ID }
            val ok = permissionEvaluation.isStatusChannelReady
            StatusCard(
                title = "表盘持续活动小图标 (Ongoing)",
                value = if (statusChan != null) {
                    if (ok) "已就绪 · 表盘小图标可用" else "已关闭 · 表盘不显示小图标"
                } else "已配置",
                isOk = ok
            )
        }

        // 实际渠道振动状态
        item {
            val breakChan = channelStatuses.find { it.channelId == NotificationChannels.CHANNEL_BREAK_ID }
            val ok = breakChan?.isVibrationEnabled == true && (breakChan.importance >= 4)
            StatusCard(
                title = "渠道：休息开始 (振动)",
                value = if (breakChan != null) {
                    "${if (breakChan.isVibrationEnabled) "振动开启" else "振动关闭"} · 重要性 ${breakChan.importance}"
                } else "未配置",
                isOk = ok
            )
        }

        item {
            val workChan = channelStatuses.find { it.channelId == NotificationChannels.CHANNEL_WORK_ID }
            val ok = workChan?.isVibrationEnabled == true && (workChan.importance >= 4)
            StatusCard(
                title = "渠道：工作开始 (振动)",
                value = if (workChan != null) {
                    "${if (workChan.isVibrationEnabled) "振动开启" else "振动关闭"} · 重要性 ${workChan.importance}"
                } else "未配置",
                isOk = ok
            )
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

        // 测试按钮 (调用真实系统通知渠道触发原生振动，不影响当前会话)
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
                        text = "试一下：开始休息提醒",
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
                        text = "试一下：开始工作提醒",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
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
