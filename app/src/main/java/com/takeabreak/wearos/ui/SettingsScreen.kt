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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
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
import com.takeabreak.wearos.timer.TimerState
import com.takeabreak.wearos.timer.TimerStatus
import com.takeabreak.wearos.ui.theme.DarkBackground
import com.takeabreak.wearos.ui.theme.PauseGray
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
            .padding(horizontal = 12.dp),
        state = listState,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Text(
                text = "设置",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            )
        }

        if (!isStopped) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .background(WarningRed.copy(alpha = 0.2f))
                        .padding(vertical = 4.dp, horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "计时中无法修改，请先停止",
                        style = MaterialTheme.typography.labelSmall,
                        color = WarningRed,
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // 工作时长
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "工作时长",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
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
                                .padding(horizontal = 2.dp)
                                .width(46.dp)
                                .height(32.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = if (selected) WorkBluePrimary else Color.White.copy(alpha = 0.12f),
                                contentColor = if (selected) DarkBackground else TextPrimary
                            )
                        ) {
                            Text(
                                text = "${minutes}m",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }

        // 休息时长
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "休息时长",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
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
                                .padding(horizontal = 2.dp)
                                .width(46.dp)
                                .height(32.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = if (selected) WorkBluePrimary else Color.White.copy(alpha = 0.12f),
                                contentColor = if (selected) DarkBackground else TextPrimary
                            )
                        ) {
                            Text(
                                text = "${minutes}m",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }

        // 提醒状态页入口
        item {
            Button(
                onClick = onOpenReminderStatus,
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(38.dp)
                    .padding(vertical = 2.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White.copy(alpha = 0.15f),
                    contentColor = TextPrimary
                )
            ) {
                Text(
                    text = "查看提醒状态",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        // 精确闹钟授权入口
        item {
            FilledTonalButton(
                onClick = onOpenExactAlarmSettings,
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(38.dp)
                    .padding(vertical = 2.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = Color.White.copy(alpha = 0.12f),
                    contentColor = TextPrimary
                )
            ) {
                Text(
                    text = "精确闹钟授权",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        // 系统通知设置
        item {
            FilledTonalButton(
                onClick = onOpenSystemNotificationSettings,
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(38.dp)
                    .padding(vertical = 2.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = Color.White.copy(alpha = 0.12f),
                    contentColor = TextSecondary
                )
            ) {
                Text(
                    text = "手表通知设置",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        // 版本信息
        item {
            val context = LocalContext.current
            val displayVersion = remember(context) {
                try {
                    val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                    "v" + (pInfo.versionName ?: "2.2.0")
                } catch (_: Exception) {
                    "v2.2.0"
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "休息一下 Wear OS $displayVersion",
                    style = MaterialTheme.typography.labelSmall,
                    color = PauseGray,
                    fontSize = 10.sp
                )
                Text(
                    text = "Galaxy Watch 7 纯原生架构",
                    style = MaterialTheme.typography.labelSmall,
                    color = PauseGray.copy(alpha = 0.7f),
                    fontSize = 9.sp
                )
            }
        }
    }
}
