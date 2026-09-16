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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.takeabreak.wearos.timer.TimerState
import com.takeabreak.wearos.timer.TimerStatus
import com.takeabreak.wearos.ui.theme.DarkBackground
import com.takeabreak.wearos.ui.theme.SurfaceDark
import com.takeabreak.wearos.ui.theme.TextMuted
import com.takeabreak.wearos.ui.theme.TextPrimary
import com.takeabreak.wearos.ui.theme.TextSecondary
import com.takeabreak.wearos.ui.theme.WarningRed
import com.takeabreak.wearos.ui.theme.WorkBluePrimary

@Composable
fun SettingsScreen(
    state: TimerState,
    onSetWorkDuration: (Int) -> Unit,
    onSetBreakDuration: (Int) -> Unit,
    onOpenReminderStatus: () -> Unit,
    onOpenSystemNotificationSettings: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onBack: () -> Unit
) {
    val listState = rememberScalingLazyListState()
    val isStopped = state.status == TimerStatus.STOPPED

    ScalingLazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(horizontal = 14.dp),
        state = listState,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Text(
                text = "偏好设置",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 12.dp, bottom = 6.dp)
            )
        }

        if (!isStopped) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(WarningRed.copy(alpha = 0.16f))
                        .padding(vertical = 6.dp, horizontal = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "计时运行中不可调整时长，请先结束",
                        style = MaterialTheme.typography.labelSmall,
                        color = WarningRed,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // 工作时长选择卡片
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceDark)
                    .padding(vertical = 8.dp, horizontal = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "工作/专注时长",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf(45, 60, 90).forEach { minutes ->
                        val selected = state.workDurationMinutes == minutes
                        FilledTonalButton(
                            onClick = { if (isStopped) onSetWorkDuration(minutes) },
                            enabled = isStopped,
                            modifier = Modifier
                                .padding(horizontal = 3.dp)
                                .weight(1f)
                                .height(36.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = if (selected) WorkBluePrimary else Color.White.copy(alpha = 0.08f),
                                contentColor = if (selected) DarkBackground else TextPrimary
                            )
                        ) {
                            Text(
                                text = "${minutes}分",
                                fontSize = 12.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }

        // 休息时长选择卡片
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceDark)
                    .padding(vertical = 8.dp, horizontal = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "休息时长",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf(3, 5, 10).forEach { minutes ->
                        val selected = state.breakDurationMinutes == minutes
                        FilledTonalButton(
                            onClick = { if (isStopped) onSetBreakDuration(minutes) },
                            enabled = isStopped,
                            modifier = Modifier
                                .padding(horizontal = 3.dp)
                                .weight(1f)
                                .height(36.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = if (selected) WorkBluePrimary else Color.White.copy(alpha = 0.08f),
                                contentColor = if (selected) DarkBackground else TextPrimary
                            )
                        ) {
                            Text(
                                text = "${minutes}分",
                                fontSize = 12.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }

        // 提醒状态与测试入口
        item {
            FilledTonalButton(
                onClick = onOpenReminderStatus,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .padding(vertical = 2.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = SurfaceDark,
                    contentColor = TextPrimary
                )
            ) {
                Text(
                    text = "提醒与权限诊断",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // 系统通知设置直达
        item {
            FilledTonalButton(
                onClick = onOpenSystemNotificationSettings,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .padding(vertical = 2.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = SurfaceDark,
                    contentColor = TextPrimary
                )
            ) {
                Text(
                    text = "手表通知设置",
                    fontSize = 12.sp
                )
            }
        }

        // 返回
        item {
            FilledTonalButton(
                onClick = onBack,
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(36.dp)
                    .padding(top = 4.dp, bottom = 12.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = Color.White.copy(alpha = 0.12f),
                    contentColor = TextSecondary
                )
            ) {
                Text(
                    text = "返回",
                    fontSize = 12.sp
                )
            }
        }
    }
}
