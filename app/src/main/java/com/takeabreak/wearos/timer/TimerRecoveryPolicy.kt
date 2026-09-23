package com.takeabreak.wearos.timer

internal data class RecoveryTiming(val remainingMs: Long, val isTrueBoot: Boolean) {
    val needsConfirmation: Boolean get() = remainingMs <= 1000L
}

/** Computes timing only. Stop journal precedence and transaction order belong to the engine. */
internal object TimerRecoveryPolicy {
    fun timing(
        current: TimerState,
        event: TimerSystemEvent,
        nowElapsed: Long,
        nowWall: Long,
        currentBootCount: Int?
    ): RecoveryTiming {
        val rebooted = event == TimerSystemEvent.BOOT_COMPLETED ||
            if (current.bootCount != null && currentBootCount != null) {
                current.bootCount != currentBootCount
            } else {
                current.bootIdentifier > 0L && nowElapsed < current.bootIdentifier
            }
        val remaining = if (rebooted) current.deadlineWallClockMs - nowWall
            else current.deadlineElapsedRealtimeMs - nowElapsed
        return RecoveryTiming(remaining.coerceAtLeast(0L), rebooted)
    }

    fun canRetryAfterPermissionGrant(state: TimerState, exactAlarmsAllowed: Boolean): Boolean =
        state.status == TimerStatus.ERROR && state.failureReason == TimerFailure.ALARM_SCHEDULING && exactAlarmsAllowed
}
