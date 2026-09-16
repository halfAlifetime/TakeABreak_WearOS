package com.takeabreak.wearos.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.takeabreak.wearos.permission.SettingTarget
import com.takeabreak.wearos.timer.TimerPhase
import com.takeabreak.wearos.timer.TimerState
import com.takeabreak.wearos.timer.TimerStatus
import com.takeabreak.wearos.ui.theme.BreakGreenContainer
import com.takeabreak.wearos.ui.theme.BreakGreenPrimary
import com.takeabreak.wearos.ui.theme.DarkBackground
import com.takeabreak.wearos.ui.theme.PauseGray
import com.takeabreak.wearos.ui.theme.TextPrimary
import com.takeabreak.wearos.ui.theme.TextSecondary
import com.takeabreak.wearos.ui.theme.WarningRed
import com.takeabreak.wearos.ui.theme.WorkBlueContainer
import com.takeabreak.wearos.ui.theme.WorkBluePrimary

@Composable
fun TimerScreen(
    state: TimerState,
    tickElapsed: Long,
    feedback: ActionFeedback? = null,
    actionMessage: String? = null,
    actionSettingTarget: SettingTarget = SettingTarget.NONE,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRetry: () -> Unit,
    onStop: () -> Unit,
    onClearActionMessage: () -> Unit = {},
    onOpenSettingTarget: (SettingTarget) -> Unit = {},
    onOpenSettings: () -> Unit
) {
    var showStopConfirmDialog by remember { mutableStateOf(false) }

    // 优先采用统一结构化的 feedback，同时兼容单一 actionMessage
    val effectiveFeedback = feedback ?: actionMessage?.let { msg ->
        val type = when {
            msg.contains("成功") || msg.contains("已重新恢复") -> FeedbackType.SUCCESS
            msg.contains("已提交") || msg.contains("提示") -> FeedbackType.INFO
            else -> FeedbackType.ERROR
        }
        ActionFeedback(message = msg, type = type, settingTarget = actionSettingTarget)
    }

    val remainingMs = state.calculateRemainingMs(tickElapsed)
    val remainingSec = (remainingMs + 999L) / 1000L
    val timeMinutes = remainingSec / 60
    val timeSeconds = remainingSec % 60
    val timeString = "%02d:%02d".format(timeMinutes, timeSeconds)

    val rawProgress = state.calculateProgress(tickElapsed)
    val animatedProgress by animateFloatAsState(
        targetValue = rawProgress,
        label = "circular_progress"
    )

    val activeColor by animateColorAsState(
        targetValue = when (state.status) {
            TimerStatus.RUNNING -> if (state.phase == TimerPhase.WORK) WorkBluePrimary else BreakGreenPrimary
            TimerStatus.PAUSED -> PauseGray
            TimerStatus.ERROR -> WarningRed
            TimerStatus.STOPPED -> WorkBluePrimary
        },
        label = "phase_color"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .drawBehind {
                if (state.status == TimerStatus.RUNNING || state.status == TimerStatus.PAUSED) {
                    val strokeWidth = 5.dp.toPx()
                    val diameter = size.minDimension - strokeWidth * 2 - 8.dp.toPx()
                    val topLeft = Offset(
                        (size.width - diameter) / 2f,
                        (size.height - diameter) / 2f
                    )

                    // 背景暗轨
                    drawArc(
                        color = Color.White.copy(alpha = 0.12f),
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = Size(diameter, diameter),
                        style = Stroke(width = strokeWidth)
                    )

                    // 剩余进度环 (从满环顺时针减少)
                    val sweep = 360f * animatedProgress
                    drawArc(
                        color = activeColor,
                        startAngle = -90f,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = Size(diameter, diameter),
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        when (state.status) {
            TimerStatus.STOPPED -> {
                StoppedView(
                    workMin = state.workDurationMinutes,
                    breakMin = state.breakDurationMinutes,
                    onStart = onStart,
                    onOpenSettings = onOpenSettings
                )
            }
            TimerStatus.RUNNING -> {
                RunningView(
                    phase = state.phase,
                    round = state.currentRound,
                    timeString = timeString,
                    activeColor = activeColor,
                    onPause = onPause,
                    onAskStop = { showStopConfirmDialog = true }
                )
            }
            TimerStatus.PAUSED -> {
                PausedView(
                    phase = state.phase,
                    timeString = timeString,
                    onResume = onResume,
                    onAskStop = { showStopConfirmDialog = true }
                )
            }
            TimerStatus.ERROR -> {
                ErrorView(
                    errorMessage = state.errorMessage ?: "计时异常中断",
                    onRetry = onRetry,
                    onAskStop = { showStopConfirmDialog = true },
                    onOpenSettings = onOpenSettings
                )
            }
        }

        // 浮层显示操作反馈与直达设置入口（统一提醒能力检查与操作反馈）
        if (effectiveFeedback != null) {
            val titleText = when (effectiveFeedback.type) {
                FeedbackType.ERROR -> "提醒受阻"
                FeedbackType.SUCCESS -> "操作成功"
                FeedbackType.INFO -> "系统提示"
            }
            val titleColor = when (effectiveFeedback.type) {
                FeedbackType.ERROR -> WarningRed
                FeedbackType.SUCCESS -> BreakGreenPrimary
                FeedbackType.INFO -> WorkBluePrimary
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.88f))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = titleText,
                        style = MaterialTheme.typography.titleMedium,
                        color = titleColor,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = effectiveFeedback.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextPrimary,
                        textAlign = TextAlign.Center,
                        maxLines = 3
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (effectiveFeedback.settingTarget != SettingTarget.NONE) {
                            Button(
                                onClick = {
                                    onOpenSettingTarget(effectiveFeedback.settingTarget)
                                    onClearActionMessage()
                                },
                                modifier = Modifier
                                    .height(34.dp)
                                    .padding(horizontal = 4.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = WorkBluePrimary,
                                    contentColor = DarkBackground
                                )
                            ) {
                                Text(
                                    text = "去设置",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        FilledTonalButton(
                            onClick = onClearActionMessage,
                            modifier = Modifier
                                .height(34.dp)
                                .padding(horizontal = 4.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = Color.White.copy(alpha = 0.15f),
                                contentColor = TextPrimary
                            )
                        ) {
                            Text(
                                text = "知道了",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }

        // 停止确认弹窗（手表上防止误触，所有停止入口共用此统一确认流程）
        if (showStopConfirmDialog) {
            StopConfirmOverlay(
                onConfirm = {
                    showStopConfirmDialog = false
                    onStop()
                },
                onCancel = { showStopConfirmDialog = false }
            )
        }
    }
}

@Composable
private fun StoppedView(
    workMin: Int,
    breakMin: Int,
    onStart: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "休息一下",
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "工作 ${workMin}m · 休息 ${breakMin}m",
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(14.dp))

        Button(
            onClick = onStart,
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .height(46.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = WorkBluePrimary,
                contentColor = DarkBackground
            )
        ) {
            Text(
                text = "开始",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        FilledTonalButton(
            onClick = onOpenSettings,
            modifier = Modifier
                .fillMaxWidth(0.56f)
                .height(34.dp),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = Color.White.copy(alpha = 0.15f),
                contentColor = TextPrimary
            )
        ) {
            Text(
                text = "设置",
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun RunningView(
    phase: TimerPhase,
    round: Int,
    timeString: String,
    activeColor: Color,
    onPause: () -> Unit,
    onAskStop: () -> Unit
) {
    val phaseLabel = if (phase == TimerPhase.WORK) "工作中" else "休息中"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "$phaseLabel · 第 $round 轮",
            style = MaterialTheme.typography.labelSmall,
            color = activeColor,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = timeString,
            fontSize = 40.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            letterSpacing = (-1).sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onPause,
                modifier = Modifier
                    .width(76.dp)
                    .height(38.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = activeColor,
                    contentColor = DarkBackground
                )
            ) {
                Text(
                    text = "暂停",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            FilledTonalButton(
                onClick = onAskStop,
                modifier = Modifier
                    .width(62.dp)
                    .height(38.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = Color.White.copy(alpha = 0.12f),
                    contentColor = TextSecondary
                )
            ) {
                Text(
                    text = "停止",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun PausedView(
    phase: TimerPhase,
    timeString: String,
    onResume: () -> Unit,
    onAskStop: () -> Unit
) {
    val pausedLabel = if (phase == TimerPhase.WORK) "工作已暂停" else "休息已暂停"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = pausedLabel,
            style = MaterialTheme.typography.labelSmall,
            color = PauseGray,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = timeString,
            fontSize = 40.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary.copy(alpha = 0.85f),
            letterSpacing = (-1).sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onResume,
                modifier = Modifier
                    .width(76.dp)
                    .height(38.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = WorkBluePrimary,
                    contentColor = DarkBackground
                )
            ) {
                Text(
                    text = "继续",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            FilledTonalButton(
                onClick = onAskStop,
                modifier = Modifier
                    .width(62.dp)
                    .height(38.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = Color.White.copy(alpha = 0.12f),
                    contentColor = TextSecondary
                )
            ) {
                Text(
                    text = "停止",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun ErrorView(
    errorMessage: String,
    onRetry: () -> Unit,
    onAskStop: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "计时已暂停",
            style = MaterialTheme.typography.titleMedium,
            color = WarningRed,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = errorMessage,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            maxLines = 2
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Button(
                onClick = onRetry,
                modifier = Modifier
                    .width(68.dp)
                    .height(34.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = WorkBluePrimary,
                    contentColor = DarkBackground
                )
            ) {
                Text(text = "重试", style = MaterialTheme.typography.labelSmall)
            }

            Spacer(modifier = Modifier.width(6.dp))

            FilledTonalButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .width(68.dp)
                    .height(34.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = Color.White.copy(alpha = 0.15f),
                    contentColor = TextPrimary
                )
            ) {
                Text(text = "去设置", style = MaterialTheme.typography.labelSmall)
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        FilledTonalButton(
            onClick = onAskStop,
            modifier = Modifier
                .width(142.dp)
                .height(28.dp),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = Color.White.copy(alpha = 0.08f),
                contentColor = TextSecondary
            )
        ) {
            Text(text = "停止并重置", fontSize = 11.sp)
        }
    }
}

@Composable
private fun StopConfirmOverlay(
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.92f))
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "确定停止计时？",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "将取消提醒并重置轮数",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                horizontalArrangement = Arrangement.Center
            ) {
                Button(
                    onClick = onConfirm,
                    modifier = Modifier
                        .width(68.dp)
                        .height(36.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = WarningRed,
                        contentColor = DarkBackground
                    )
                ) {
                    Text(text = "停止", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.width(10.dp))

                FilledTonalButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .width(68.dp)
                        .height(36.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Color.White.copy(alpha = 0.15f),
                        contentColor = TextPrimary
                    )
                ) {
                    Text(text = "取消", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
