package com.takeabreak.wearos.timer

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class TimerRulesTest {
    @Test fun phaseCycleKeepsRestRoundAndIncrementsNextWorkRound() {
        val work = TimerState(currentRound = 3, workDurationMinutes = 45, breakDurationMinutes = 10)
        assertEquals(PhaseTarget(TimerPhase.BREAK, 3, 10), TimerTransitions.nextPhase(work))
        assertEquals(PhaseTarget(TimerPhase.WORK, 4, 45), TimerTransitions.nextPhase(work.copy(phase = TimerPhase.BREAK)))
    }

    @Test fun resumeWithTimeLeftRetainsPhaseAndOriginalProgressScale() {
        val state = TimerState(phase = TimerPhase.BREAK, currentRound = 3, pausedRemainingMs = 20_000, phaseTotalDurationMs = 60_000)
        assertEquals(ResumeTarget(TimerPhase.BREAK, 3, 20_000, 60_000, false), TimerTransitions.resume(state))
    }

    @Test fun expiredResumeAdvancesExactlyOnePhase() {
        val work = TimerState(currentRound = 3, workDurationMinutes = 45, breakDurationMinutes = 10, pausedRemainingMs = 0)
        assertEquals(ResumeTarget(TimerPhase.BREAK, 3, 600_000, 600_000, true), TimerTransitions.resume(work))
        assertEquals(ResumeTarget(TimerPhase.WORK, 4, 2_700_000, 2_700_000, true), TimerTransitions.resume(work.copy(phase = TimerPhase.BREAK)))
    }

    @Test fun legacyInvalidRemainingKeepsExistingFallbackRules() {
        val state = TimerState(phase = TimerPhase.BREAK, currentRound = 4, pausedRemainingMs = -1, phaseTotalDurationMs = 60_000)
        assertEquals(ResumeTarget(TimerPhase.BREAK, 4, 60_000, 60_000, false), TimerTransitions.resume(state))
        assertEquals(ResumeTarget(TimerPhase.WORK, 1, 3_600_000, 3_600_000, false), TimerTransitions.resume(state.copy(phaseTotalDurationMs = 0)))
    }

    @Test fun wallClockChangeWithinSameBootUsesElapsedDeadline() {
        val state = TimerState(deadlineElapsedRealtimeMs = 10_000, deadlineWallClockMs = 20_000, bootCount = 7)
        val result = TimerRecoveryPolicy.timing(state, TimerSystemEvent.TIME_CHANGED, 5_000, 500_000, 7)
        assertEquals(RecoveryTiming(5_000, false), result)
    }

    @Test fun actualBootUsesWallClockAndClampsMissedDeadline() {
        val state = TimerState(deadlineElapsedRealtimeMs = 10_000, deadlineWallClockMs = 20_000, bootCount = 7)
        assertEquals(RecoveryTiming(2_000, true), TimerRecoveryPolicy.timing(state, TimerSystemEvent.PACKAGE_REPLACED, 50, 18_000, 8))
        val expired = TimerRecoveryPolicy.timing(state, TimerSystemEvent.BOOT_COMPLETED, 50, 30_000, 7)
        assertEquals(RecoveryTiming(0, true), expired)
        assertTrue(expired.needsConfirmation)
    }

    @Test fun legacyBootIdentifierFallbackAndOneSecondBoundaryArePreserved() {
        val state = TimerState(bootIdentifier = 1000, deadlineWallClockMs = 20_000, deadlineElapsedRealtimeMs = 5000)
        assertEquals(RecoveryTiming(1000, true), TimerRecoveryPolicy.timing(state, TimerSystemEvent.TIME_CHANGED, 100, 19_000, null))
        assertTrue(RecoveryTiming(1000, false).needsConfirmation)
        assertFalse(RecoveryTiming(1001, false).needsConfirmation)
    }

    @Test fun permissionRecoveryDependsOnFailureCodeNotMessage() = runBlocking {
        val repo = FakeTimerRepository()
        repo.updateTimerState { TimerState(sessionId = "saved", status = TimerStatus.ERROR, pausedRemainingMs = 20_000,
            failureReason = TimerFailure.ALARM_SCHEDULING, errorMessage = "arbitrary wording") }
        val engine = TimerEngine(repo, FakeAlarmScheduler(), FakeReminderNotifier(), FakeClockProvider(), FakeStopIntentStore())
        val recovered = engine.onSystemEvent(TimerSystemEvent.EXACT_ALARM_PERMISSION_CHANGED)
        assertEquals(TimerStatus.RUNNING, recovered.status)
        assertNull(recovered.failureReason)
        assertNull(recovered.errorMessage)
    }

    @Test fun storageAndUnknownFailuresNeverAutoResumeBasedOnChineseText() = runBlocking {
        for (failure in listOf(TimerFailure.STORAGE_READ, TimerFailure.STORAGE_WRITE, TimerFailure.UNKNOWN, null)) {
            val repo = FakeTimerRepository()
            repo.updateTimerState { TimerState(sessionId = "saved", status = TimerStatus.ERROR, pausedRemainingMs = 20_000,
                failureReason = failure, errorMessage = "闹钟权限已恢复", lastEventResult = "闹钟权限") }
            val scheduler = FakeAlarmScheduler()
            val engine = TimerEngine(repo, scheduler, FakeReminderNotifier(), FakeClockProvider(), FakeStopIntentStore())
            assertEquals(TimerStatus.ERROR, engine.onSystemEvent(TimerSystemEvent.EXACT_ALARM_PERMISSION_CHANGED).status)
            assertTrue(scheduler.scheduledStates.isEmpty())
            assertTrue(engine.retry().isSuccess)
        }
    }

    @Test fun stoppingClearsTypedFailureWithoutChangingPreferences() = runBlocking {
        val repo = FakeTimerRepository()
        val scheduler = FakeAlarmScheduler().apply { canSchedule = false }
        val engine = TimerEngine(repo, scheduler, FakeReminderNotifier(), FakeClockProvider(), FakeStopIntentStore())
        assertTrue(engine.start(customWorkMin = 45, customBreakMin = 10).isFailure)
        assertEquals(TimerFailure.ALARM_SCHEDULING, engine.getTimerState().failureReason)
        val stopped = engine.stop().getOrThrow()
        assertEquals(45, stopped.workDurationMinutes)
        assertEquals(10, stopped.breakDurationMinutes)
        assertNull(stopped.failureReason)
    }
}
