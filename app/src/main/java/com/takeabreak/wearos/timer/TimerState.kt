package com.takeabreak.wearos.timer

enum class TimerStatus {
    STOPPED,
    RUNNING,
    PAUSED,
    ERROR
}

enum class TimerPhase {
    WORK,
    BREAK
}

data class TimerState(
    val sessionId: String = "",
    val generation: Long = 0L,
    val status: TimerStatus = TimerStatus.STOPPED,
    val phase: TimerPhase = TimerPhase.WORK,
    val currentRound: Int = 1,
    val workDurationMinutes: Int = 60,
    val breakDurationMinutes: Int = 5,
    val phaseTotalDurationMs: Long = 60 * 60 * 1000L,
    val deadlineElapsedRealtimeMs: Long = 0L,
    val deadlineWallClockMs: Long = 0L,
    val pausedRemainingMs: Long = 0L,
    val bootIdentifier: Long = 0L,
    val lastEventId: String = "",
    val lastEventResult: String = "",
    val lastEventTimestampMs: Long = 0L,
    val errorMessage: String? = null
) {
    /**
     * 计算当前实际剩余毫秒数
     */
    fun calculateRemainingMs(nowElapsedMs: Long): Long {
        return when (status) {
            TimerStatus.RUNNING -> (deadlineElapsedRealtimeMs - nowElapsedMs).coerceAtLeast(0L)
            TimerStatus.PAUSED, TimerStatus.ERROR -> pausedRemainingMs.coerceAtLeast(0L)
            TimerStatus.STOPPED -> 0L
        }
    }

    val isError: Boolean
        get() = status == TimerStatus.ERROR

    /**
     * 计算环形进度 (1.0f 降至 0.0f)
     */
    fun calculateProgress(nowElapsedMs: Long): Float {
        if (phaseTotalDurationMs <= 0L) return 0f
        val remaining = calculateRemainingMs(nowElapsedMs)
        return (remaining.toFloat() / phaseTotalDurationMs.toFloat()).coerceIn(0f, 1f)
    }

    /**
     * 格式化剩余时间为 mm:ss
     */
    fun formattedRemainingTime(nowElapsedMs: Long): String {
        val remainingSec = (calculateRemainingMs(nowElapsedMs) + 999L) / 1000L
        val minutes = remainingSec / 60
        val seconds = remainingSec % 60
        return "%02d:%02d".format(minutes, seconds)
    }
}

sealed class PhaseTransitionResult {
    data class Success(val newState: TimerState) : PhaseTransitionResult()
    data class Ignored(val reason: String) : PhaseTransitionResult()
    data class PrematureHandled(val reason: String) : PhaseTransitionResult()
    data class Failure(val errorReason: String, val newState: TimerState) : PhaseTransitionResult()
}
