package com.takeabreak.wearos.ui

import kotlin.math.sin

import kotlin.math.PI

import androidx.compose.ui.graphics.drawscope.Stroke

import androidx.compose.ui.graphics.StrokeCap

import androidx.compose.ui.graphics.Path

import androidx.compose.animation.core.tween

import androidx.compose.animation.core.rememberInfiniteTransition

import androidx.compose.animation.core.infiniteRepeatable

import androidx.compose.animation.core.LinearEasing

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.takeabreak.wearos.permission.SettingTarget
import com.takeabreak.wearos.timer.TimerPhase
import com.takeabreak.wearos.timer.TimerState
import com.takeabreak.wearos.timer.TimerStatus
import com.takeabreak.wearos.ui.theme.BreakAmberGlow
import com.takeabreak.wearos.ui.theme.BreakAmberPrimary
import com.takeabreak.wearos.ui.theme.BreakAmberText
import com.takeabreak.wearos.ui.theme.DangerRedContainer
import com.takeabreak.wearos.ui.theme.DarkBackground
import com.takeabreak.wearos.ui.theme.FocusMintGlow
import com.takeabreak.wearos.ui.theme.FocusMintPrimary
import com.takeabreak.wearos.ui.theme.FocusMintText
import com.takeabreak.wearos.ui.theme.PauseGray
import com.takeabreak.wearos.ui.theme.TextMuted
import com.takeabreak.wearos.ui.theme.TextPrimary
import com.takeabreak.wearos.ui.theme.TextSecondary
import com.takeabreak.wearos.ui.theme.WarningRed

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
        label = "liquid_progress"
    )

    val isFocus = state.phase == TimerPhase.WORK
    val activePrimaryColor by animateColorAsState(
        targetValue = when (state.status) {
            TimerStatus.RUNNING -> if (isFocus) FocusMintPrimary else BreakAmberPrimary
            TimerStatus.PAUSED -> PauseGray
            TimerStatus.ERROR -> WarningRed
            TimerStatus.STOPPED -> FocusMintPrimary
        },
        label = "liquid_primary_color"
    )

    val activeGlowColor by animateColorAsState(
        targetValue = when (state.status) {
            TimerStatus.RUNNING -> if (isFocus) FocusMintGlow else BreakAmberGlow
            TimerStatus.PAUSED -> PauseGray
            TimerStatus.ERROR -> WarningRed
            TimerStatus.STOPPED -> FocusMintGlow
        },
        label = "liquid_glow_color"
    )

    // 真实水波动态相位：无限平滑循环驱动波浪起伏流动
    val infiniteTransition = rememberInfiniteTransition(label = "wave_motion")
    val wavePhase1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200, easing = LinearEasing)
        ),
        label = "wave_phase_1"
    )
    val wavePhase2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4600, easing = LinearEasing)
        ),
        label = "wave_phase_2"
    )

    // 水波液面高度：随倒计时平稳升降，在底部约 20%~42% 之间展现通透水面
    val liquidHeightRatio = when (state.status) {
        TimerStatus.RUNNING, TimerStatus.PAUSED -> 0.22f + (1f - animatedProgress) * 0.18f
        TimerStatus.STOPPED -> 0.22f
        TimerStatus.ERROR -> 0.18f
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .drawBehind {
                // 绘制真实物理双层流动的波浪水体
                val liquidHeightPx = size.height * liquidHeightRatio
                val baseWaterTopY = size.height - liquidHeightPx
                val waveAmp1 = 6.dp.toPx()
                val waveAmp2 = 4.5.dp.toPx()

                // 1. 后层微光波浪 (Back Wave)：差速流动，营造水体透视深度
                val backWavePath = Path().apply {
                    moveTo(0f, size.height)
                    var x = 0f
                    val step = 8f
                    while (x <= size.width + step) {
                        val curX = x.coerceAtMost(size.width)
                        val angle = (curX / size.width * 2f * PI.toFloat() * 1.15f) - wavePhase2
                        val curY = baseWaterTopY - 3.dp.toPx() + (sin(angle.toDouble()).toFloat() * waveAmp2)
                        lineTo(curX, curY)
                        x += step
                    }
                    lineTo(size.width, size.height)
                    close()
                }
                drawPath(
                    path = backWavePath,
                    brush = Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to activePrimaryColor.copy(alpha = 0.07f),
                            0.4f to activePrimaryColor.copy(alpha = 0.16f),
                            1.0f to activeGlowColor.copy(alpha = 0.35f)
                        ),
                        startY = (baseWaterTopY - waveAmp2 - 12.dp.toPx()).coerceAtLeast(0f),
                        endY = size.height
                    )
                )

                // 2. 前层主水波 (Front Wave)：双谐波复合物理起伏，细腻流动
                val frontWavePath = Path()
                val crestPath = Path()
                var firstPoint = true
                var curX = 0f
                val step = 6f
                frontWavePath.moveTo(0f, size.height)

                while (curX <= size.width + step) {
                    val px = curX.coerceAtMost(size.width)
                    val angle1 = (px / size.width * 2f * PI.toFloat()) + wavePhase1
                    val angle2 = (px / size.width * 4f * PI.toFloat()) - (wavePhase2 * 0.8f)
                    val harmonic1 = sin(angle1.toDouble()).toFloat() * waveAmp1
                    val harmonic2 = sin(angle2.toDouble()).toFloat() * (waveAmp1 * 0.3f)
                    val curY = baseWaterTopY + harmonic1 + harmonic2

                    if (firstPoint) {
                        frontWavePath.lineTo(px, curY)
                        crestPath.moveTo(px, curY)
                        firstPoint = false
                    } else {
                        frontWavePath.lineTo(px, curY)
                        crestPath.lineTo(px, curY)
                    }
                    curX += step
                }
                frontWavePath.lineTo(size.width, size.height)
                frontWavePath.close()

                // 填充前层水体
                drawPath(
                    path = frontWavePath,
                    brush = Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to activePrimaryColor.copy(alpha = 0.12f),
                            0.35f to activePrimaryColor.copy(alpha = 0.28f),
                            1.0f to activeGlowColor.copy(alpha = 0.65f)
                        ),
                        startY = (baseWaterTopY - waveAmp1 * 1.5f).coerceAtLeast(0f),
                        endY = size.height
                    )
                )

                // 3. 水波表面柔光波纹轮廓 (Wave Crest Shimmer)：极轻柔微光，勾勒流动起伏，无厚重生硬感
                drawPath(
                    path = crestPath,
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            activePrimaryColor.copy(alpha = 0.10f),
                            activePrimaryColor.copy(alpha = 0.45f),
                            activePrimaryColor.copy(alpha = 0.10f)
                        )
                    ),
                    style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)
                )
            },
        contentAlignment = Alignment.Center
    ) {
        when (state.status) {
            TimerStatus.STOPPED -> {
                StoppedZenView(
                    workMin = state.workDurationMinutes,
                    breakMin = state.breakDurationMinutes,
                    onStart = onStart,
                    onOpenSettings = onOpenSettings
                )
            }
            TimerStatus.RUNNING -> {
                RunningZenView(
                    isFocus = isFocus,
                    timeString = timeString,
                    activeColor = activePrimaryColor,
                    activeTextColor = if (isFocus) FocusMintText else BreakAmberText,
                    onPause = onPause,
                    onAskStop = { showStopConfirmDialog = true },
                    onOpenSettings = onOpenSettings
                )
            }
            TimerStatus.PAUSED -> {
                PausedZenView(
                    isFocus = isFocus,
                    timeString = timeString,
                    onResume = onResume,
                    onAskStop = { showStopConfirmDialog = true },
                    onOpenSettings = onOpenSettings
                )
            }
            TimerStatus.ERROR -> {
                ErrorZenView(
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
                FeedbackType.ERROR -> "提示"
                FeedbackType.SUCCESS -> "完成"
                FeedbackType.INFO -> "信息"
            }
            val titleColor = when (effectiveFeedback.type) {
                FeedbackType.ERROR -> WarningRed
                FeedbackType.SUCCESS -> FocusMintPrimary
                FeedbackType.INFO -> FocusMintText
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
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = titleColor
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
                                    containerColor = FocusMintPrimary,
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

        // 停止确认弹窗 (全中文极简)
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
 * 运行中状态 (禅意水波沙漏 · 极简留白最终版)
 * 12点钟: ⚙️ 极简设置按钮
 * 中央: 超大轻量细线等宽倒计时 + 纯净副标「专注中 / 放松中」
 * 6点钟: 对称双圆按键 (暂停 + 结束重置)
 */
@Composable
private fun RunningZenView(
    isFocus: Boolean,
    timeString: String,
    activeColor: Color,
    activeTextColor: Color,
    onPause: () -> Unit,
    onAskStop: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val subText = if (isFocus) "专注中" else "放松中"

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // 顶部 12 点钟：设置齿轮
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 18.dp)
                .size(34.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.10f))
                .border(1.dp, activeColor.copy(alpha = 0.35f), CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onOpenSettings
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "设置",
                tint = activeTextColor,
                modifier = Modifier.size(16.dp)
            )
        }

        // 中间倒计时 + 极简副标 (超大轻量细线等宽数字)
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(bottom = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = timeString,
                fontSize = 50.sp,
                fontWeight = FontWeight.ExtraLight,
                fontFamily = FontFamily.SansSerif,
                color = TextPrimary,
                letterSpacing = 1.5.sp
            )
            Text(
                text = subText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = activeTextColor,
                letterSpacing = 4.sp
            )
        }

        // 底部 6 点钟：对称双圆按键
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 暂停按键
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(activeColor.copy(alpha = 0.22f))
                    .border(1.5.dp, activeColor.copy(alpha = 0.55f), CircleShape)
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
                    tint = activeTextColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            // 结束重置按键
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(DangerRedContainer)
                    .border(1.5.dp, WarningRed.copy(alpha = 0.50f), CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onAskStop
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = "结束",
                    tint = WarningRed,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * 暂停状态
 */
@Composable
private fun PausedZenView(
    isFocus: Boolean,
    timeString: String,
    onResume: () -> Unit,
    onAskStop: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val subText = if (isFocus) "专注已暂停" else "放松已暂停"

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // 顶部 12 点钟：设置齿轮
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 18.dp)
                .size(34.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.10f))
                .border(1.dp, Color.White.copy(alpha = 0.20f), CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onOpenSettings
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "设置",
                tint = TextSecondary,
                modifier = Modifier.size(16.dp)
            )
        }

        // 中间倒计时 + 暂停副标
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(bottom = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = timeString,
                fontSize = 50.sp,
                fontWeight = FontWeight.ExtraLight,
                fontFamily = FontFamily.SansSerif,
                color = TextPrimary.copy(alpha = 0.65f),
                letterSpacing = 1.5.sp
            )
            Text(
                text = subText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                color = PauseGray,
                letterSpacing = 3.sp
            )
        }

        // 底部 6 点钟：继续 / 结束
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 继续按键
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(FocusMintPrimary.copy(alpha = 0.25f))
                    .border(1.5.dp, FocusMintPrimary.copy(alpha = 0.60f), CircleShape)
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
                    tint = FocusMintText,
                    modifier = Modifier.size(24.dp)
                )
            }

            // 结束重置按键
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(DangerRedContainer)
                    .border(1.5.dp, WarningRed.copy(alpha = 0.50f), CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onAskStop
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = "结束",
                    tint = WarningRed,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * 准备就绪 / 待机状态
 */
@Composable
private fun StoppedZenView(
    workMin: Int,
    breakMin: Int,
    onStart: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // 顶部 12 点钟：设置齿轮
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 18.dp)
                .size(34.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.10f))
                .border(1.dp, FocusMintPrimary.copy(alpha = 0.35f), CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onOpenSettings
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "设置",
                tint = FocusMintText,
                modifier = Modifier.size(16.dp)
            )
        }

        // 中间大号预设时长
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(bottom = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "%02d:00".format(workMin),
                fontSize = 50.sp,
                fontWeight = FontWeight.ExtraLight,
                fontFamily = FontFamily.SansSerif,
                color = TextPrimary,
                letterSpacing = 1.5.sp
            )
            Text(
                text = "休息  分钟",
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                color = FocusMintText,
                letterSpacing = 3.sp
            )
        }

        // 底部 6 点钟：开始大按键
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 18.dp)
                .size(48.dp)
                .clip(CircleShape)
                .background(FocusMintPrimary)
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
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

@Composable
private fun ErrorZenView(
    errorMessage: String,
    onRetry: () -> Unit,
    onAskStop: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "计时中断",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = WarningRed
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(FocusMintPrimary.copy(alpha = 0.25f))
                    .clickable(onClick = onRetry),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "重试",
                    tint = FocusMintText,
                    modifier = Modifier.size(20.dp)
                )
            }

            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(DangerRedContainer)
                    .clickable(onClick = onAskStop),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = "重置",
                    tint = WarningRed,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * 极简结束二次防误触确认弹窗
 */
@Composable
private fun StopConfirmOverlay(
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.94f))
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "结束计时",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = WarningRed
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "确认提前结束本次专注吗？",
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
                        .width(66.dp)
                        .height(36.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Color.White.copy(alpha = 0.15f),
                        contentColor = TextPrimary
                    )
                ) {
                    Text(
                        text = "取消",
                        style = MaterialTheme.typography.labelSmall
                    )
                }

                Button(
                    onClick = onConfirm,
                    modifier = Modifier
                        .width(66.dp)
                        .height(36.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = WarningRed,
                        contentColor = TextPrimary
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
