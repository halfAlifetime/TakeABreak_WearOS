package com.takeabreak.wearos.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.takeabreak.wearos.permission.SettingTarget
import com.takeabreak.wearos.timer.TimerPhase
import com.takeabreak.wearos.timer.TimerState
import com.takeabreak.wearos.timer.TimerStatus
import com.takeabreak.wearos.ui.theme.BreakAmberText
import com.takeabreak.wearos.ui.theme.FocusMintText

@Composable
fun TimerScreen(
    state: TimerState,
    tickElapsed: Long,
    feedback: ActionFeedback? = null,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRetry: () -> Unit,
    onStop: () -> Unit,
    onClearFeedback: () -> Unit = {},
    onOpenSettingTarget: (SettingTarget) -> Unit = {},
    onOpenSettings: () -> Unit
) {
    var showStopConfirmDialog by remember { mutableStateOf(false) }

    val remainingMs = state.calculateRemainingMs(tickElapsed)
    val remainingSec = (remainingMs + 999L) / 1000L
    val timeMinutes = remainingSec / 60
    val timeSeconds = remainingSec % 60
    val timeString = "%02d:%02d".format(timeMinutes, timeSeconds)

    val isFocus = state.phase == TimerPhase.WORK

    TimerWaterBackground(state, tickElapsed) { activePrimaryColor ->
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
                    onAskStop = { showStopConfirmDialog = true }
                )
            }
        }

        feedback?.let {
            TimerFeedbackDialog(it, onClearFeedback, onOpenSettingTarget)
        }

        // 停止确认弹窗 (全中文极简)
        if (showStopConfirmDialog) {
            StopConfirmDialog(
                onConfirm = {
                    showStopConfirmDialog = false
                    onStop()
                },
                onCancel = { showStopConfirmDialog = false }
            )
        }
    }
}
