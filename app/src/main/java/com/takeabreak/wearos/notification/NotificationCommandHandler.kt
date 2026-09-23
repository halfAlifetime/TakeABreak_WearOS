package com.takeabreak.wearos.notification

import com.takeabreak.wearos.timer.TimerCommand
import com.takeabreak.wearos.timer.TimerEngine
import com.takeabreak.wearos.timer.TimerState

/** Wire decoding only. The engine runs session validation and preflight under one lock. */
class NotificationCommandHandler(private val engine: TimerEngine) {
    suspend fun handle(
        action: String?,
        expectedSessionId: String?,
        preflightChecker: () -> Result<Unit> = { Result.success(Unit) }
    ): Result<TimerState> {
        val command = when (action) {
            NotificationActions.PAUSE -> TimerCommand.PAUSE
            NotificationActions.RESUME -> TimerCommand.RESUME
            NotificationActions.STOP -> TimerCommand.STOP
            else -> return Result.failure(IllegalArgumentException("未知通知动作: $action"))
        }
        return engine.handleSessionCommand(command, expectedSessionId, preflightChecker)
    }
}
