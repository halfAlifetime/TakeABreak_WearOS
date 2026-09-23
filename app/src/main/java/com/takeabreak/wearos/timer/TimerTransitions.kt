package com.takeabreak.wearos.timer

internal data class PhaseTarget(val phase: TimerPhase, val round: Int, val durationMinutes: Int) {
    val durationMs: Long get() = durationMinutes * 60_000L
}

internal data class ResumeTarget(
    val phase: TimerPhase,
    val round: Int,
    val durationMs: Long,
    val totalDurationMs: Long,
    val advancesPhase: Boolean
)

/** Pure phase calculations. Scheduling, persistence and publication remain in TimerEngine. */
internal object TimerTransitions {
    fun nextPhase(current: TimerState): PhaseTarget = when (current.phase) {
        TimerPhase.WORK -> PhaseTarget(TimerPhase.BREAK, current.currentRound, current.breakDurationMinutes)
        TimerPhase.BREAK -> PhaseTarget(TimerPhase.WORK, current.currentRound + 1, current.workDurationMinutes)
    }

    fun resume(current: TimerState): ResumeTarget {
        val next = when {
            current.pausedRemainingMs > 0L -> Triple(current.phase, current.currentRound, current.pausedRemainingMs)
            current.pausedRemainingMs == 0L -> nextPhase(current).let { Triple(it.phase, it.round, it.durationMs) }
            current.phaseTotalDurationMs > 0L -> Triple(current.phase, current.currentRound, current.phaseTotalDurationMs)
            else -> Triple(TimerPhase.WORK, 1, current.workDurationMinutes * 60_000L)
        }
        val (phase, round, remaining) = next
        val total = when {
            phase != current.phase -> remaining
            current.phaseTotalDurationMs > 0L -> current.phaseTotalDurationMs
            phase == TimerPhase.WORK -> current.workDurationMinutes * 60_000L
            else -> current.breakDurationMinutes * 60_000L
        }
        return ResumeTarget(phase, round, remaining, total, current.pausedRemainingMs == 0L)
    }
}
