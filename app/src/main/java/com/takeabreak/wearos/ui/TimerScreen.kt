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

                // 运动手表的背景暗轨（精细半透明暗环）
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
                    .background(Color.Black.copy(alpha = 0.90f))
                    .padding(20.dp),
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
                                text = "知道了",
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
 * 待机准备状态：Apple/Samsung 运动极简美学
 * 顶部精致胶囊配置 -> 中间大卡片启动按键 -> 底部设置入口
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
        // 顶部小胶囊徽章：点击也可以直达设置调整
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.10f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onOpenSettings
                )
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Text(
                text = "${workMin}m 工作 · ${breakMin}m 休息",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextSecondary
            )
        }

        // 中间主视觉：标志性的大圆形启动按键，带有微发光与极简 Play 符号
        Box(
            modifier = Modifier
                .size(80.dp)
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
                contentDescription = "开始",
                tint = DarkBackground,
                modifier = Modifier.size(44.dp)
            )
        }

        // 底部微型设置按钮
        IconButton(
            onClick = onOpenSettings,
            modifier = Modifier.size(34.dp),
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = Color.White.copy(alpha = 0.10f),
                contentColor = TextSecondary
            )
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "设置",
                modifier = Modifier.size(17.dp)
            )
        }
    }
}

/**
 * 运行中状态：
 * 顶部阶段与轮次药丸胶囊 -> 中心 44sp 极粗大数字时间 -> 底部双圆形微型控制键（暂停 / 停止）
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
    val phaseLabel = if (phase == TimerPhase.WORK) "专注中" else "休息放松"
    val phaseDotColor = if (phase == TimerPhase.WORK) WorkBluePrimary else BreakGreenPrimary

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // 顶部阶段药丸标签
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(activeColor.copy(alpha = 0.16f))
                .padding(horizontal = 10.dp, vertical = 3.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(phaseDotColor)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "$phaseLabel · 第 $round 轮",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = activeColor
                )
            }
        }

        // 中心超大倒计时
        Text(
            text = timeString,
            fontSize = 44.sp,
            fontWeight = FontWeight.Black,
            color = TextPrimary,
            letterSpacing = (-1.5).sp,
            modifier = Modifier.padding(bottom = 2.dp)
        )

        // 底部双控制操作：圆形图标按钮，极具质感
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 暂停按钮
            Box(
                modifier = Modifier
                    .size(46.dp)
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
                    contentDescription = "暂停",
                    tint = DarkBackground,
                    modifier = Modifier.size(24.dp)
                )
            }

            // 停止按钮
            Box(
                modifier = Modifier
                    .size(46.dp)
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
                    contentDescription = "停止",
                    tint = WarningRed,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

/**
 * 暂停状态：
 * 柔和色调 + 呼吸感
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
    val pausedLabel = if (phase == TimerPhase.WORK) "专注已暂停" else "休息已暂停"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // 顶部已暂停药丸标签
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.10f))
                .padding(horizontal = 10.dp, vertical = 3.dp)
        ) {
            Text(
                text = "$pausedLabel · 第 $round 轮",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = PauseGray
            )
        }

        // 中心超大倒计时 (半透白，视觉呈暂停状态)
        Text(
            text = timeString,
            fontSize = 44.sp,
            fontWeight = FontWeight.Black,
            color = TextPrimary.copy(alpha = 0.75f),
            letterSpacing = (-1.5).sp,
            modifier = Modifier.padding(bottom = 2.dp)
        )

        // 底部双控制操作
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 继续运行按钮
            Box(
                modifier = Modifier
                    .size(46.dp)
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
                    contentDescription = "继续",
                    tint = DarkBackground,
                    modifier = Modifier.size(26.dp)
                )
            }

            // 停止按钮
            Box(
                modifier = Modifier
                    .size(46.dp)
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
                    contentDescription = "停止",
                    tint = WarningRed,
                    modifier = Modifier.size(22.dp)
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
            text = "状态异常",
            style = MaterialTheme.typography.titleMedium,
            color = WarningRed,
            fontWeight = FontWeight.Bold
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
                    text = "重试",
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
                    text = "重置",
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
                text = "结束计时",
                style = MaterialTheme.typography.titleMedium,
                color = WarningRed,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "确定要提前结束当前轮次吗？",
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
                        text = "继续",
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
                        text = "结束",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
