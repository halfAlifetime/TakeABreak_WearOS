package com.takeabreak.wearos.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.takeabreak.wearos.permission.SettingTarget
import com.takeabreak.wearos.timer.TimerPhase
import com.takeabreak.wearos.timer.TimerState
import com.takeabreak.wearos.timer.TimerStatus
import com.takeabreak.wearos.ui.theme.BreakGreenPrimary
import com.takeabreak.wearos.ui.theme.DarkBackground
import com.takeabreak.wearos.ui.theme.PauseGray
import com.takeabreak.wearos.ui.theme.TextMuted
import com.takeabreak.wearos.ui.theme.TextPrimary
import com.takeabreak.wearos.ui.theme.TextSecondary
import com.takeabreak.wearos.ui.theme.WarningRed
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
        animationSpec = spring(stiffness = Spring.StiffnessLow),
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
                val strokeWidth = 8.dp.toPx()
                val diameter = size.minDimension - strokeWidth * 2 - 10.dp.toPx()
                val topLeft = Offset(
                    (size.width - diameter) / 2f,
                    (size.height - diameter) / 2f
                )

                // 原生运动表盘半透底环
                drawArc(
                    color = Color.White.copy(alpha = 0.08f),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = Size(diameter, diameter),
                    style = Stroke(width = strokeWidth)
                )

                if (state.status == TimerStatus.RUNNING || state.status == TimerStatus.PAUSED) {
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
                    onAskStop = { showStopConfirmDialog = true },
                    onOpenSettings = onOpenSettings
                )
            }
            TimerStatus.PAUSED -> {
                PausedView(
                    phase = state.phase,
                    round = state.currentRound,
                    timeString = timeString,
                    onResume = onResume,
                    onAskStop = { showStopConfirmDialog = true },
                    onOpenSettings = onOpenSettings
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

        // 操作反馈浮层
        if (effectiveFeedback != null) {
            val titleText = when (effectiveFeedback.type) {
                FeedbackType.ERROR -> "NOTICE"
                FeedbackType.SUCCESS -> "SUCCESS"
                FeedbackType.INFO -> "INFO"
            }
            val titleColor = when (effectiveFeedback.type) {
                FeedbackType.ERROR -> WarningRed
                FeedbackType.SUCCESS -> BreakGreenPrimary
                FeedbackType.INFO -> WorkBluePrimary
            }

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
                        text = titleText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        color = titleColor,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = effectiveFeedback.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextPrimary,
                        textAlign = TextAlign.Center,
                        maxLines = 3
                    )
                    Spacer(modifier = Modifier.height(12.dp))
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
                                    .height(36.dp)
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
                                .height(36.dp)
                                .padding(horizontal = 4.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = Color.White.copy(alpha = 0.16f),
                                contentColor = TextPrimary
                            )
                        ) {
                            Text(
                                text = "好",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }

        // 停止确认弹窗
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

/**
 * 待机准备状态（方向 1：极致国际化极简）：
 * 顶部纯净大预设时长标签 (60 MIN · REST 5M) -> 黄金比例主圆形按键 -> 底部设置按键
 */
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
            .padding(horizontal = 16.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // 顶部预设：极简排版，大写字重，Apple Watch 经典气质
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onOpenSettings
                )
                .padding(top = 2.dp)
        ) {
            Text(
                text = "$workMin MIN",
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
                color = TextPrimary,
                letterSpacing = 1.2.sp
            )
            Text(
                text = "REST ${breakMin}M",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = TextMuted,
                letterSpacing = 1.sp
            )
        }

        // 中间主视觉：纯粹大圆环启动器
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            WorkBluePrimary,
                            WorkBluePrimary.copy(alpha = 0.85f)
                        )
                    )
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onStart
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Start",
                tint = DarkBackground,
                modifier = Modifier.size(42.dp)
            )
        }

        // 底部设置按钮
        IconButton(
            onClick = onOpenSettings,
            modifier = Modifier.size(32.dp),
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = Color.White.copy(alpha = 0.08f),
                contentColor = TextSecondary
            )
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings",
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/**
 * 运行中状态（方向 1）：
 * 顶部大写状态词 FOCUS / BREAK -> 中心超大等宽时间 -> 底部辅助 ROUND 1 + 纯图标控制
 */
@Composable
private fun RunningView(
    phase: TimerPhase,
    round: Int,
    timeString: String,
    activeColor: Color,
    onPause: () -> Unit,
    onAskStop: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val phaseTag = if (phase == TimerPhase.WORK) "FOCUS" else "REST"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // 顶部阶段指示：大写英文字符，质感高级
        Text(
            text = phaseTag,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            color = activeColor,
            letterSpacing = 2.5.sp,
            modifier = Modifier.padding(top = 4.dp)
        )

        // 中心超大时间 + 轮次小标
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = timeString,
                fontSize = 46.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                color = TextPrimary,
                letterSpacing = (-1.5).sp
            )
            Text(
                text = "ROUND $round",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = TextMuted,
                letterSpacing = 1.2.sp
            )
        }

        // 底部双圆形控制键
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 暂停
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(activeColor)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onPause
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Pause,
                    contentDescription = "Pause",
                    tint = DarkBackground,
                    modifier = Modifier.size(22.dp)
                )
            }

            // 停止
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.12f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onAskStop
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = "Stop",
                    tint = WarningRed,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * 暂停状态（方向 1）
 */
@Composable
private fun PausedView(
    phase: TimerPhase,
    round: Int,
    timeString: String,
    onResume: () -> Unit,
    onAskStop: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val phaseTag = if (phase == TimerPhase.WORK) "FOCUS PAUSED" else "REST PAUSED"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // 顶部状态
        Text(
            text = phaseTag,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = PauseGray,
            letterSpacing = 2.sp,
            modifier = Modifier.padding(top = 4.dp)
        )

        // 中心超大时间
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = timeString,
                fontSize = 46.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                color = TextPrimary.copy(alpha = 0.70f),
                letterSpacing = (-1.5).sp
            )
            Text(
                text = "ROUND $round",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = TextMuted.copy(alpha = 0.70f),
                letterSpacing = 1.2.sp
            )
        }

        // 底部双控制操作
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 继续
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(WorkBluePrimary)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onResume
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Resume",
                    tint = DarkBackground,
                    modifier = Modifier.size(24.dp)
                )
            }

            // 停止
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.12f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onAskStop
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = "Stop",
                    tint = WarningRed,
                    modifier = Modifier.size(20.dp)
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
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "ERROR",
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            color = WarningRed,
            letterSpacing = 1.5.sp
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = errorMessage,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            maxLines = 2
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onRetry,
                modifier = Modifier
                    .width(74.dp)
                    .height(36.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = WorkBluePrimary,
                    contentColor = DarkBackground
                )
            ) {
                Text(
                    text = "RETRY",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            FilledTonalButton(
                onClick = onAskStop,
                modifier = Modifier
                    .width(62.dp)
                    .height(36.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = Color.White.copy(alpha = 0.15f),
                    contentColor = WarningRed
                )
            ) {
                Text(
                    text = "RESET",
                    style = MaterialTheme.typography.labelSmall
                )
            }
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
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "END SESSION",
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                color = WarningRed,
                letterSpacing = 1.5.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "End the current timer?",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .width(68.dp)
                        .height(38.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Color.White.copy(alpha = 0.16f),
                        contentColor = TextPrimary
                    )
                ) {
                    Text(
                        text = "NO",
                        style = MaterialTheme.typography.labelSmall
                    )
                }

                Button(
                    onClick = onConfirm,
                    modifier = Modifier
                        .width(68.dp)
                        .height(38.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = WarningRed,
                        contentColor = DarkBackground
                    )
                ) {
                    Text(
                        text = "YES",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
