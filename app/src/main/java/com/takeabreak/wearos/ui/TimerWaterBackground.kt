package com.takeabreak.wearos.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.takeabreak.wearos.timer.TimerPhase
import com.takeabreak.wearos.timer.TimerState
import com.takeabreak.wearos.timer.TimerStatus
import com.takeabreak.wearos.ui.theme.BreakAmberGlow
import com.takeabreak.wearos.ui.theme.BreakAmberPrimary
import com.takeabreak.wearos.ui.theme.DarkBackground
import com.takeabreak.wearos.ui.theme.FocusMintGlow
import com.takeabreak.wearos.ui.theme.FocusMintPrimary
import com.takeabreak.wearos.ui.theme.PauseGray
import com.takeabreak.wearos.ui.theme.WarningRed
import kotlin.math.PI
import kotlin.math.sin

/** Decorative only: elapsed time and status come from the caller. */
@Composable
internal fun TimerWaterBackground(
    state: TimerState,
    tickElapsed: Long,
    content: @Composable BoxScope.(Color) -> Unit
) {
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

    // Only animate a running timer while the screen is interactive.
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val (wavePhase1, wavePhase2) = if (
        state.status == TimerStatus.RUNNING && lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    ) {
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
        wavePhase1 to wavePhase2
    } else {
        0f to 0f
    }

    // 水波液面高度：水波沙漏随时间流逝逐渐填满（从底部 10% 慢慢上升填满到 95%）
    // animatedProgress 从 1.0f -> 0.0f；已耗时进度 elapsedRatio 为 (1f - animatedProgress) 从 0.0f -> 1.0f
    val elapsedRatio = (1f - animatedProgress).coerceIn(0f, 1f)
    val liquidHeightRatio = when (state.status) {
        TimerStatus.RUNNING, TimerStatus.PAUSED -> 0.10f + elapsedRatio * 0.85f
        TimerStatus.STOPPED -> 0.12f
        TimerStatus.ERROR -> 0.10f
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
        content(activePrimaryColor)
    }
}
