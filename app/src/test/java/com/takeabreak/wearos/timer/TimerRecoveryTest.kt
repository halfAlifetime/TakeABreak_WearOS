package com.takeabreak.wearos.timer

import com.takeabreak.wearos.timer.support.FakeReminderNotifier
import com.takeabreak.wearos.timer.support.FakeAlarmScheduler
import com.takeabreak.wearos.timer.support.FakeStopIntentStore
import com.takeabreak.wearos.timer.support.FakeTimerRepository
import com.takeabreak.wearos.timer.support.FakeClockProvider
import com.takeabreak.wearos.notification.NotificationActions
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.*
import org.junit.Test

class TimerRecoveryTest {
    private val clock = FakeClockProvider()
    private val repo = FakeTimerRepository()
    private val scheduler = FakeAlarmScheduler()
    private val notifier = FakeReminderNotifier()
    private val journal = FakeStopIntentStore()
    private fun newEngine() = TimerEngine(repo, scheduler, notifier, clock, journal)
    private val engine = newEngine()

    @Test
    fun notificationProtocolSupportsPauseResumeAndStop() = runBlocking {
        val started = engine.start().getOrThrow()
        assertTrue(engine.handleNotificationAction(NotificationActions.PAUSE, started.sessionId).isSuccess)
        assertEquals(TimerStatus.PAUSED, engine.getTimerState().status)
        assertTrue(engine.handleNotificationAction(NotificationActions.RESUME, started.sessionId).isSuccess)
        assertEquals(TimerStatus.RUNNING, engine.getTimerState().status)
        assertTrue(engine.handleNotificationAction(NotificationActions.STOP, started.sessionId).isSuccess)
        assertEquals(TimerStatus.STOPPED, engine.getTimerState().status)
        assertTrue(scheduler.activeAlarms.isEmpty())
    }

    @Test
    fun notificationWithoutSessionCannotControlCurrentTimer() = runBlocking {
        val started = engine.start().getOrThrow()
        for (session in listOf(null, "", "old-session")) {
            assertTrue(engine.handleNotificationAction(NotificationActions.STOP, session).isFailure)
        }
        assertEquals(started, engine.getTimerState())
    }

    @Test
    fun staleRepositoryEmissionCannotReplaceEffectiveErrorWithRunning() = runBlocking {
        val started = engine.start().getOrThrow()
        scheduler.canSchedule = false
        repo.failWrites = true
        assertTrue(engine.onPhaseAlarm(started.sessionId, started.generation, started.phase, 1)
            is PhaseTransitionResult.Failure)
        assertEquals(TimerStatus.RUNNING, repo.getTimerState().status)
        assertEquals(TimerStatus.ERROR, engine.timerStateFlow.first().status)
        val staleRunning = withTimeoutOrNull(200) {
            engine.timerStateFlow.first { it.status == TimerStatus.RUNNING }
        }
        assertNull(staleRunning)
        assertEquals(TimerStatus.ERROR, engine.getTimerState().status)
        assertTrue(scheduler.activeAlarms.isEmpty())
    }

    @Test
    fun failedStopSurvivesProcessRecreationAndRejectsOldAlarm() = runBlocking {
        val started = engine.start().getOrThrow()
        repo.failWrites = true
        assertTrue(engine.stop().isFailure)
        assertEquals(TimerStatus.RUNNING, repo.getTimerState().status)
        assertEquals(started.generation, journal.generation)
        repo.failWrites = false

        val recreated = newEngine()
        assertEquals(TimerStatus.STOPPED, recreated.timerStateFlow.first().status)
        assertTrue(recreated.onPhaseAlarm(started.sessionId, started.generation, started.phase, 1)
            is PhaseTransitionResult.Ignored)
        assertEquals(TimerStatus.STOPPED, recreated.onSystemEvent(TimerSystemEvent.TIME_CHANGED).status)
        assertEquals(1, scheduler.scheduledStates.size)
        assertTrue(scheduler.activeAlarms.isEmpty())
        assertTrue(notifier.phaseReminders.isEmpty())

        val next = recreated.start().getOrThrow()
        assertNotEquals(started.sessionId, next.sessionId)
        assertEquals(TimerStatus.RUNNING, newEngine().getTimerState().status)
    }

    @Test
    fun stopAfterInitialReadFailureBlocksUnknownPersistedGeneration() = runBlocking {
        val original = engine.start().getOrThrow()
        repo.failReads = true
        repo.failWrites = true
        val unreadableEngine = newEngine()
        assertTrue(unreadableEngine.stop().isFailure)
        assertEquals(TimerStatus.STOPPED, unreadableEngine.getTimerState().status)
        // The generation could not be read; the session gate must protect larger values too.
        assertTrue(original.generation > journal.generation)
        assertTrue(journal.isRecoveryBlocked(original.sessionId))
        assertTrue(scheduler.activeAlarms.isEmpty())

        repo.failReads = false
        repo.failWrites = false
        assertEquals(original, repo.getTimerState())
        val recreated = newEngine()
        assertTrue(recreated.onPhaseAlarm(original.sessionId, original.generation, original.phase, 1)
            is PhaseTransitionResult.Ignored)
        assertTrue(recreated.resume().isFailure)
        assertEquals(TimerStatus.STOPPED, recreated.onSystemEvent(TimerSystemEvent.TIME_CHANGED).status)
        assertEquals(TimerStatus.STOPPED, recreated.onSystemEvent(TimerSystemEvent.BOOT_COMPLETED).status)
        assertEquals(1, scheduler.scheduledStates.size)
        assertTrue(scheduler.activeAlarms.isEmpty())
        assertTrue(notifier.phaseReminders.isEmpty())

        val next = recreated.start().getOrThrow()
        assertNotEquals(original.sessionId, next.sessionId)
        assertTrue(next.generation > original.generation)
        assertTrue(journal.isRecoveryBlocked(original.sessionId))
        assertFalse(journal.isRecoveryBlocked(next.sessionId))
        assertEquals(next, newEngine().getTimerState())
    }

    @Test
    fun failedExplicitStartDoesNotUnblockUnknownOldSession() = runBlocking {
        val original = engine.start().getOrThrow()
        repo.failReads = true
        repo.failWrites = true
        val unreadableEngine = newEngine()
        assertTrue(unreadableEngine.stop().isFailure)
        assertTrue(unreadableEngine.start().isFailure)
        val attemptedSessionId = unreadableEngine.getTimerState().sessionId
        assertNotEquals(original.sessionId, attemptedSessionId)
        assertFalse(journal.isRecoveryBlocked(attemptedSessionId))
        assertTrue(journal.isRecoveryBlocked(original.sessionId))

        repo.failReads = false
        repo.failWrites = false
        assertEquals(original, repo.getTimerState())
        val recreated = newEngine()
        assertEquals(TimerStatus.STOPPED, recreated.onSystemEvent(TimerSystemEvent.TIME_CHANGED).status)
        assertTrue(scheduler.activeAlarms.isEmpty())
        assertEquals(TimerStatus.STOPPED, recreated.getTimerState().status)
    }

    @Test
    fun startAuthorizationWriteFailureKeepsTimerStopped() = runBlocking {
        val original = engine.start().getOrThrow()
        repo.failReads = true
        repo.failWrites = true
        val unreadableEngine = newEngine()
        assertTrue(unreadableEngine.stop().isFailure)
        repo.failReads = false
        repo.failWrites = false
        journal.failAllowStart = true

        assertTrue(unreadableEngine.start().isFailure)
        assertEquals(TimerStatus.STOPPED, unreadableEngine.getTimerState().status)
        assertEquals(original, repo.getTimerState())
        assertEquals(1, scheduler.scheduledStates.size)
        assertTrue(scheduler.activeAlarms.isEmpty())
        assertTrue(journal.isRecoveryBlocked(original.sessionId))
        assertEquals(TimerStatus.STOPPED, newEngine().getTimerState().status)

        journal.failAllowStart = false
        val next = unreadableEngine.start().getOrThrow()
        assertEquals(next, newEngine().getTimerState())
    }

    @Test
    fun recreationDuringNewStartCannotRecoverPreviousSession() = runBlocking {
        val original = engine.start().getOrThrow()
        repo.failReads = true
        repo.failWrites = true
        val unreadableEngine = newEngine()
        assertTrue(unreadableEngine.stop().isFailure)
        repo.failReads = false
        repo.failWrites = false
        val writing = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        repo.beforeUpdate = { writing.complete(Unit); release.await() }
        val start = launch { unreadableEngine.start().getOrThrow() }
        try {
            withTimeout(5_000) { writing.await() }
            assertEquals(original, repo.getTimerState())
            assertTrue(journal.isRecoveryBlocked(original.sessionId))
            assertEquals(TimerStatus.STOPPED, newEngine().getTimerState().status)
        } finally {
            release.complete(Unit)
        }
        withTimeout(5_000) { start.join() }
        val next = unreadableEngine.getTimerState()
        assertEquals(TimerStatus.RUNNING, next.status)
        assertEquals(next, newEngine().getTimerState())
    }

    @Test
    fun failedSchedulingCanRetryTheExplicitlyAuthorizedNewSession() = runBlocking {
        val original = engine.start().getOrThrow()
        engine.stop().getOrThrow()
        scheduler.canSchedule = false
        assertTrue(engine.start().isFailure)
        val attemptedSessionId = engine.getTimerState().sessionId
        assertTrue(journal.isRecoveryBlocked(original.sessionId))
        assertFalse(journal.isRecoveryBlocked(attemptedSessionId))

        scheduler.canSchedule = true
        val resumed = engine.retry().getOrThrow()
        assertEquals(attemptedSessionId, resumed.sessionId)
        assertEquals(resumed, newEngine().getTimerState())
    }

    @Test
    fun legacyGenerationOnlyStopRecordRemainsCompatible() = runBlocking {
        val original = engine.start().getOrThrow()
        journal.generation = original.generation
        journal.allowedSessionId = null
        val recreated = newEngine()
        assertEquals(TimerStatus.STOPPED, recreated.getTimerState().status)
        val next = recreated.start().getOrThrow()
        assertTrue(next.generation > journal.generation)
        assertEquals(next, newEngine().getTimerState())
    }

    @Test
    fun stopCanPersistMainStateWhenJournalWriteFails() = runBlocking {
        engine.start().getOrThrow()
        journal.failWrites = true
        assertTrue(engine.stop().isSuccess)
        assertEquals(TimerStatus.STOPPED, newEngine().getTimerState().status)
    }

    @Test
    fun multipleFailedStartStopAttemptsNeverUnblockAnOlderPersistedSession() = runBlocking {
        val original = engine.start().getOrThrow()
        repo.failWrites = true
        assertTrue(engine.stop().isFailure)
        assertTrue(engine.start().isFailure)
        assertTrue(engine.stop().isFailure)
        assertTrue(journal.generation > original.generation)
        assertEquals(original, repo.getTimerState())

        val recreated = newEngine()
        assertEquals(TimerStatus.STOPPED, recreated.getTimerState().status)
        assertEquals(journal.generation, recreated.getTimerState().generation)
        repo.failWrites = false
        val next = recreated.start().getOrThrow()
        assertTrue(next.generation > journal.generation)
        assertEquals(next, newEngine().getTimerState())
    }

    @Test
    fun failedStopReportsWhenBothStoresFail() = runBlocking {
        engine.start().getOrThrow()
        repo.failWrites = true
        journal.failWrites = true
        val result = engine.stop()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("停止记录保存失败"))
        assertEquals(TimerStatus.STOPPED, engine.getTimerState().status)
        assertTrue(scheduler.activeAlarms.isEmpty())
    }

    @Test
    fun resumeAfterBootRefreshesClockAnchorBeforeWallClockChange() = runBlocking {
        clock.elapsed = 1_000_000L
        engine.start().getOrThrow()
        clock.advance(60_000)
        engine.pause().getOrThrow()
        val remaining = engine.getTimerState().pausedRemainingMs
        clock.elapsed = 5_000
        clock.wall += 10_000
        val recreated = newEngine()
        recreated.onSystemEvent(TimerSystemEvent.BOOT_COMPLETED)
        val resumed = recreated.resume().getOrThrow()
        assertEquals(clock.elapsed, resumed.bootIdentifier)
        clock.wall += 3_600_000
        val reconciled = recreated.onSystemEvent(TimerSystemEvent.TIME_CHANGED)
        assertEquals(TimerStatus.RUNNING, reconciled.status)
        assertEquals(remaining, reconciled.calculateRemainingMs(clock.elapsed))
    }

    @Test
    fun bootCounterDetectsRebootEvenWhenNewUptimeExceedsOldAnchor() = runBlocking {
        clock.boot = 4
        engine.start().getOrThrow()
        clock.boot = 5
        clock.wall += 1_800_000
        clock.elapsed += 500_000
        val reconciled = newEngine().onSystemEvent(TimerSystemEvent.TIME_CHANGED)
        assertEquals(1_800_000L, reconciled.calculateRemainingMs(clock.elapsed))
        assertEquals(5, reconciled.bootCount)
    }

    @Test
    fun permissionGrantReschedulesRunningTimer() = runBlocking {
        engine.start().getOrThrow()
        scheduler.activeAlarms.clear() // The OS removed alarms while permission was revoked.
        val reconciled = engine.onSystemEvent(TimerSystemEvent.EXACT_ALARM_PERMISSION_CHANGED)
        assertEquals(reconciled, scheduler.activeAlarms[reconciled.generation])
        assertEquals(TimerStatus.RUNNING, reconciled.status)
    }

    @Test
    fun wrongRoundAlarmCannotChangeStateOrNotify() = runBlocking {
        val started = engine.start().getOrThrow()
        clock.advance(3_600_000)
        assertTrue(engine.onPhaseAlarm(started.sessionId, started.generation, started.phase, 99)
            is PhaseTransitionResult.Ignored)
        assertEquals(started, engine.getTimerState())
        assertTrue(notifier.phaseReminders.isEmpty())
    }

    @Test
    fun durationWriteFailureDoesNotPublishUnsavedPreferences() = runBlocking {
        val original = engine.getTimerState()
        repo.failWrites = true
        assertTrue(engine.updateDurations(45, 3).isFailure)
        assertEquals(original, engine.getTimerState())
        assertEquals(original, repo.getTimerState())
    }

    @Test
    fun invalidDurationsNeverScheduleAnAlarm() = runBlocking {
        assertTrue(engine.start(customWorkMin = 0).isFailure)
        assertTrue(engine.updateDurations(45, -1).isFailure)
        assertTrue(scheduler.scheduledStates.isEmpty())
        assertEquals(TimerStatus.STOPPED, engine.getTimerState().status)
    }

    private enum class Command { START, PAUSE, RESUME, STOP, PHASE, RECONCILE }

    private fun cancelledCallerStillCompletesTransaction(command: Command) = runBlocking {
        if (command != Command.START) engine.start().getOrThrow()
        if (command == Command.RESUME) engine.pause().getOrThrow()
        val before = engine.getTimerState()
        if (command == Command.PHASE) clock.advance(3_600_000)
        val writing = CompletableDeferred<Unit>()
        val releaseWrite = CompletableDeferred<Unit>()
        repo.beforeUpdate = {
            writing.complete(Unit)
            releaseWrite.await()
        }
        val caller = launch {
            when (command) {
                Command.START -> engine.start()
                Command.PAUSE -> engine.pause()
                Command.RESUME -> engine.resume()
                Command.STOP -> engine.stop()
                Command.PHASE -> engine.onPhaseAlarm(before.sessionId, before.generation, before.phase, 1)
                Command.RECONCILE -> engine.onSystemEvent(TimerSystemEvent.TIME_CHANGED)
            }
        }
        try {
            withTimeout(5_000) { writing.await() }
            caller.cancel()
        } finally {
            releaseWrite.complete(Unit)
        }
        withTimeout(5_000) { caller.join() }
        val effective = engine.getTimerState()
        val expectedStatus = when (command) {
            Command.PAUSE -> TimerStatus.PAUSED
            Command.STOP -> TimerStatus.STOPPED
            else -> TimerStatus.RUNNING
        }
        assertTrue(caller.isCancelled)
        assertEquals(expectedStatus, effective.status)
        assertEquals(effective, repo.getTimerState())
        if (expectedStatus == TimerStatus.RUNNING) {
            assertEquals(listOf(effective), scheduler.activeAlarms.values.toList())
        } else assertTrue(scheduler.activeAlarms.isEmpty())
        if (command == Command.PHASE) {
            assertEquals(TimerPhase.BREAK, effective.phase)
            assertEquals(1, notifier.phaseReminders.size)
        }
    }

    @Test fun cancelledStartCommits() = cancelledCallerStillCompletesTransaction(Command.START)
    @Test fun cancelledPauseCommits() = cancelledCallerStillCompletesTransaction(Command.PAUSE)
    @Test fun cancelledResumeCommits() = cancelledCallerStillCompletesTransaction(Command.RESUME)
    @Test fun cancelledStopCommits() = cancelledCallerStillCompletesTransaction(Command.STOP)
    @Test fun cancelledPhaseTransitionCommits() = cancelledCallerStillCompletesTransaction(Command.PHASE)
    @Test fun cancelledReconciliationCommits() = cancelledCallerStillCompletesTransaction(Command.RECONCILE)

    @Test
    fun cancellationWhileWaitingForLockDoesNotStartACommand() = runBlocking {
        engine.start().getOrThrow()
        val writing = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        repo.beforeUpdate = { writing.complete(Unit); release.await() }
        val pause = launch { engine.pause() }
        try {
            withTimeout(5_000) { writing.await() }
            val resume = launch(start = CoroutineStart.UNDISPATCHED) { engine.resume() }
            resume.cancel()
            withTimeout(5_000) { resume.join() }
        } finally {
            release.complete(Unit)
        }
        withTimeout(5_000) { pause.join() }
        assertEquals(TimerStatus.PAUSED, engine.getTimerState().status)
        assertTrue(scheduler.activeAlarms.isEmpty())
    }
}
