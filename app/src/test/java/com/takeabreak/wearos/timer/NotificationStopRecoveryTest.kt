package com.takeabreak.wearos.timer

import android.content.Intent
import com.takeabreak.wearos.notification.NotificationActions
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class NotificationStopRecoveryTest {
    private val clock = FakeClockProvider()
    private val repo = FakeTimerRepository()
    private val scheduler = FakeAlarmScheduler()
    private val notifier = FakeReminderNotifier()
    private val journal = FakeStopIntentStore()
    private fun engine(store: StopIntentStore = journal) = TimerEngine(repo, scheduler, notifier, clock, store)

    private fun reloadedJournal() = FakeStopIntentStore().also {
        it.generation = journal.generation
        it.allowedSessionId = journal.allowedSessionId
        it.stoppedSessions.addAll(journal.stoppedSessions)
    }

    @Test
    fun coldNotificationStopSurvivesReadAndWriteFailures() = runBlocking {
        val original = engine().start().getOrThrow()
        repo.failReads = true
        repo.failWrites = true
        val cold = engine()
        val result = cold.handleNotificationAction(NotificationActions.STOP, original.sessionId)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("已保存"))
        assertTrue(journal.isRecoveryBlocked(original.sessionId))
        assertTrue(scheduler.activeAlarms.isEmpty())
        assertNull(notifier.statusShown)
        assertEquals(TimerStatus.ERROR, cold.getTimerState().status)

        repo.failReads = false
        repo.failWrites = false
        assertEquals(original, repo.getTimerState())
        assertEquals(TimerStatus.STOPPED, cold.getTimerState().status)
        val recreated = engine(reloadedJournal())
        assertTrue(recreated.onPhaseAlarm(original.sessionId, original.generation, original.phase, 1)
            is PhaseTransitionResult.Ignored)
        assertEquals(TimerStatus.STOPPED, recreated.onSystemEvent(Intent.ACTION_TIME_CHANGED).status)
        assertEquals(TimerStatus.STOPPED, recreated.onSystemEvent(Intent.ACTION_BOOT_COMPLETED).status)
        assertTrue(recreated.resume().isFailure)
        assertEquals(1, scheduler.scheduledStates.size)
        assertTrue(scheduler.activeAlarms.isEmpty())
        assertTrue(notifier.phaseReminders.isEmpty())
    }

    @Test
    fun atomicStopPreservesPreferencesWhenOnlyInitialReadsFail() = runBlocking {
        val original = engine().start(customWorkMin = 45, customBreakMin = 3).getOrThrow()
        repo.failReads = true
        val stopped = engine().handleNotificationAction(NotificationActions.STOP, original.sessionId).getOrThrow()
        assertEquals(TimerStatus.STOPPED, stopped.status)
        assertEquals(45, stopped.workDurationMinutes)
        assertEquals(3, stopped.breakDurationMinutes)
        assertEquals(original.generation + 1L, stopped.generation)
        assertTrue(scheduler.activeAlarms.isEmpty())
        repo.failReads = false
        assertEquals(stopped, repo.getTimerState())
    }

    @Test
    fun staleNotificationWithUnreadableStorageCannotStopNewSession() = runBlocking {
        val warm = engine()
        val old = warm.start().getOrThrow()
        warm.stop().getOrThrow()
        val current = warm.start().getOrThrow()
        val cancellations = scheduler.allAlarmsCancelledCount
        repo.failReads = true
        repo.failWrites = true
        val cold = engine()
        cold.handleNotificationAction(NotificationActions.STOP, old.sessionId)
        cold.onSystemEvent(Intent.ACTION_TIME_CHANGED)
        cold.onSystemEvent(Intent.ACTION_BOOT_COMPLETED)
        assertEquals(cancellations, scheduler.allAlarmsCancelledCount)
        assertEquals(listOf(current), scheduler.activeAlarms.values.toList())
        assertEquals(current, notifier.statusShown)
        assertFalse(journal.isRecoveryBlocked(current.sessionId))

        repo.failReads = false
        repo.failWrites = false
        assertEquals(current, cold.getTimerState())
        assertEquals(current, engine(reloadedJournal()).getTimerState())
        assertEquals(current, repo.getTimerState())
    }

    @Test
    fun staleNotificationCannotOverwriteNewSessionInAtomicUpdate() = runBlocking {
        val warm = engine()
        val old = warm.start().getOrThrow()
        warm.stop().getOrThrow()
        val current = warm.start().getOrThrow()
        repo.failReads = true
        val cold = engine()
        assertTrue(cold.handleNotificationAction(NotificationActions.STOP, old.sessionId).isFailure)
        assertEquals(current, cold.getTimerState())
        assertEquals(current, notifier.statusShown)
        assertEquals(listOf(current), scheduler.activeAlarms.values.toList())
        repo.failReads = false
        assertEquals(current, repo.getTimerState())
    }

    @Test
    fun missingSessionAndOtherActionsDoNotCreateUnknownStopRequests() = runBlocking {
        val original = engine().start().getOrThrow()
        repo.failReads = true
        repo.failWrites = true
        val cold = engine()
        for (session in listOf(null, "")) {
            assertTrue(cold.handleNotificationAction(NotificationActions.STOP, session).isFailure)
        }
        for (action in listOf(NotificationActions.PAUSE, NotificationActions.RESUME, "unknown-action")) {
            assertTrue(cold.handleNotificationAction(action, original.sessionId).isFailure)
        }
        assertTrue(cold.pause().isFailure)
        assertTrue(cold.retry().isFailure)
        assertEquals(TimerStatus.ERROR, cold.timerStateFlow.first().status)
        assertTrue(journal.stoppedSessions.isEmpty())
        assertEquals(listOf(original), scheduler.activeAlarms.values.toList())

        repo.failReads = false
        assertEquals(original, cold.getTimerState())
    }

    @Test
    fun reportsFailureWhenNeitherStopRecordCanBeWritten() = runBlocking {
        val original = engine().start().getOrThrow()
        repo.failReads = true
        repo.failWrites = true
        journal.failWrites = true
        val cold = engine()
        val result = cold.handleNotificationAction(NotificationActions.STOP, original.sessionId)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("保存失败"))
        assertFalse(journal.isRecoveryBlocked(original.sessionId))
        assertTrue(scheduler.activeAlarms.isEmpty())
        repo.failReads = false
        assertEquals(TimerStatus.STOPPED, cold.getTimerState().status)
    }

    @Test
    fun mainStoreCanCommitStopWhenJournalWriteFails() = runBlocking {
        val original = engine().start().getOrThrow()
        repo.failReads = true
        journal.failWrites = true
        assertTrue(engine().handleNotificationAction(NotificationActions.STOP, original.sessionId).isSuccess)
        repo.failReads = false
        assertEquals(TimerStatus.STOPPED, engine(reloadedJournal()).getTimerState().status)
        assertTrue(scheduler.activeAlarms.isEmpty())
    }

    @Test
    fun legacyAlarmWithoutSessionMetadataCannotNotifyAfterPendingStop() = runBlocking {
        val original = engine().start().getOrThrow()
        scheduler.knowsSessionIdentity = false
        repo.failReads = true
        repo.failWrites = true
        engine().handleNotificationAction(NotificationActions.STOP, original.sessionId)
        // Legacy alarms cannot be selectively cancelled, but their eventual delivery is rejected.
        assertEquals(1, scheduler.activeAlarms.size)
        scheduler.activeAlarms.remove(original.generation) // OS consumes the one-shot alarm.
        clock.advance(original.phaseTotalDurationMs)
        val recreated = engine(reloadedJournal())
        assertTrue(recreated.onPhaseAlarm(original.sessionId, original.generation, original.phase, 1)
            is PhaseTransitionResult.Ignored)
        assertEquals(1, scheduler.scheduledStates.size)
        assertTrue(notifier.phaseReminders.isEmpty())
        repo.failReads = false
        assertEquals(TimerStatus.STOPPED, recreated.getTimerState().status)
    }

    @Test
    fun explicitStartAfterPendingStopAllowsOnlyNewSession() = runBlocking {
        val original = engine().start().getOrThrow()
        repo.failReads = true
        repo.failWrites = true
        val cold = engine()
        cold.handleNotificationAction(NotificationActions.STOP, original.sessionId)
        repo.failWrites = false
        val next = cold.start().getOrThrow()
        assertNotEquals(original.sessionId, next.sessionId)
        assertTrue(journal.isRecoveryBlocked(original.sessionId))
        assertFalse(journal.isRecoveryBlocked(next.sessionId))
        repo.failReads = false
        assertEquals(next, engine(reloadedJournal()).getTimerState())
        assertEquals(listOf(next), scheduler.activeAlarms.values.toList())
    }
}
