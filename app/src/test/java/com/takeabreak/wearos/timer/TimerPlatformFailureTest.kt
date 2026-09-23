package com.takeabreak.wearos.timer

import com.takeabreak.wearos.alarm.AlarmScheduler
import com.takeabreak.wearos.notification.ReminderNotifier
import com.takeabreak.wearos.timer.support.FakeAlarmScheduler
import com.takeabreak.wearos.timer.support.FakeClockProvider
import com.takeabreak.wearos.timer.support.FakeReminderNotifier
import com.takeabreak.wearos.timer.support.FakeStopIntentStore
import com.takeabreak.wearos.timer.support.FakeTimerRepository
import kotlinx.coroutines.runBlocking
import java.io.IOException
import org.junit.Assert.*
import org.junit.Test

class TimerPlatformFailureTest {
    @Test fun failedAlarmCleanupAndStorageStillPreserveStopAcrossRecreation() = runBlocking {
        val repository = FakeTimerRepository()
        val alarms = FakeAlarmScheduler()
        var failCleanup = false
        val scheduler = object : AlarmScheduler by alarms {
            override fun cancelPhaseAlarm(generation: Long) {
                if (failCleanup) throw SecurityException("cannot cancel alarm")
                alarms.cancelPhaseAlarm(generation)
            }
            override fun cancelAllPhaseAlarms() {
                if (failCleanup) throw SecurityException("cannot cancel alarms")
                alarms.cancelAllPhaseAlarms()
            }
        }
        val notifier = FakeReminderNotifier()
        val clock = FakeClockProvider()
        val journal = FakeStopIntentStore()
        fun newEngine() = TimerEngine(repository, scheduler, notifier, clock, journal)
        val engine = newEngine()
        val started = engine.start(1, 1).getOrThrow()
        failCleanup = true
        repository.failWrites = true

        assertTrue(engine.stop().isFailure)
        assertEquals(TimerStatus.STOPPED, engine.getTimerState().status)
        assertEquals(started, repository.getTimerState())
        assertTrue(alarms.activeAlarms.isNotEmpty()) // Simulate an OS alarm that could not be removed.
        assertNull(notifier.statusShown) // Other cleanup still ran.
        val recreated = newEngine()
        assertEquals(TimerStatus.STOPPED, recreated.getTimerState().status)
        clock.advance(60_000)
        assertTrue(recreated.onPhaseAlarm(started.sessionId, started.generation, started.phase, 1)
            is PhaseTransitionResult.Ignored)
        assertTrue(notifier.phaseReminders.isEmpty())
        assertEquals(1, alarms.scheduledStates.size)
    }

    @Test fun phaseNotificationFailureKeepsCommittedStateAndNextAlarm() = runBlocking {
        val repository = FakeTimerRepository()
        val alarms = FakeAlarmScheduler()
        val notifications = FakeReminderNotifier()
        val notifier = object : ReminderNotifier by notifications {
            override fun showPhaseReminder(phase: TimerPhase, round: Int, durationMinutes: Int, sessionId: String, generation: Long) {
                throw SecurityException("cannot publish phase reminder")
            }
        }
        val clock = FakeClockProvider()
        val engine = TimerEngine(repository, alarms, notifier, clock, FakeStopIntentStore())
        val started = engine.start(1, 1).getOrThrow()
        clock.advance(60_000)
        val result = engine.onPhaseAlarm(started.sessionId, started.generation, started.phase, 1)
        assertTrue(result is PhaseTransitionResult.Success)
        val saved = repository.getTimerState()
        assertEquals(TimerPhase.BREAK, saved.phase)
        assertEquals(TimerStatus.RUNNING, saved.status)
        assertEquals(saved, engine.getTimerState())
        assertEquals(listOf(saved), alarms.activeAlarms.values.toList())
        assertEquals(saved, notifications.statusShown)
    }

    private enum class ScheduleEntry { START, RESUME, PREMATURE_ALARM, RECONCILE }

    @Test fun schedulingExceptionsUseTheFailurePathForEveryEntry() = runBlocking {
        for (entry in ScheduleEntry.entries) {
            val repository = FakeTimerRepository()
            val alarms = FakeAlarmScheduler()
            var failScheduling = false
            val scheduler = object : AlarmScheduler by alarms {
                override fun schedulePhaseAlarm(state: TimerState, previousGeneration: Long?): Boolean {
                    if (failScheduling) throw IOException("cannot access alarm service")
                    return alarms.schedulePhaseAlarm(state, previousGeneration)
                }
            }
            val clock = FakeClockProvider()
            val notifier = object : ReminderNotifier by FakeReminderNotifier() {
                override fun showErrorNotification(message: String, sessionId: String) {
                    throw SecurityException("cannot publish error notification")
                }
            }
            val engine = TimerEngine(repository, scheduler, notifier, clock, FakeStopIntentStore())
            if (entry != ScheduleEntry.START) engine.start(1, 1).getOrThrow()
            if (entry == ScheduleEntry.RESUME) engine.pause().getOrThrow()
            val before = engine.getTimerState()
            failScheduling = true
            when (entry) {
                ScheduleEntry.START -> assertTrue(engine.start(1, 1).isFailure)
                ScheduleEntry.RESUME -> assertTrue(engine.resume().isFailure)
                ScheduleEntry.PREMATURE_ALARM -> assertTrue(engine.onPhaseAlarm(before.sessionId, before.generation, before.phase, 1)
                    is PhaseTransitionResult.Failure)
                ScheduleEntry.RECONCILE -> engine.onSystemEvent(TimerSystemEvent.TIME_CHANGED)
            }
            val failed = engine.getTimerState()
            assertEquals(entry.name, TimerStatus.ERROR, failed.status)
            assertEquals(entry.name, TimerFailure.ALARM_SCHEDULING, failed.failureReason)
            assertEquals(entry.name, failed, repository.getTimerState())
            assertTrue(entry.name, alarms.activeAlarms.isEmpty())
        }
    }

    @Test fun startPreservesRecoverableErrorButRetryCanResumeIt() = runBlocking {
        val repository = FakeTimerRepository()
        val error = TimerState(sessionId = "recoverable", generation = 7, status = TimerStatus.ERROR,
            phase = TimerPhase.BREAK, currentRound = 3, phaseTotalDurationMs = 300_000,
            pausedRemainingMs = 90_000, failureReason = TimerFailure.ALARM_SCHEDULING)
        repository.updateTimerState { error }
        val alarms = FakeAlarmScheduler()
        val engine = TimerEngine(repository, alarms, FakeReminderNotifier(), FakeClockProvider(), FakeStopIntentStore())
        assertTrue(engine.start().isFailure)
        assertEquals(error, repository.getTimerState())
        assertEquals(error, engine.getTimerState())
        assertTrue(alarms.activeAlarms.isEmpty())
        val resumed = engine.retry().getOrThrow()
        assertEquals(error.sessionId, resumed.sessionId)
        assertEquals(error.phase, resumed.phase)
        assertEquals(error.currentRound, resumed.currentRound)
        assertEquals(90_000L, resumed.calculateRemainingMs(100_000L))
    }

    @Test fun startCannotReplaceUnreadableSessionWithoutExplicitStop() = runBlocking {
        val repository = FakeTimerRepository()
        val persisted = TimerState(sessionId = "unreadable", generation = 5, status = TimerStatus.PAUSED)
        repository.updateTimerState { persisted }
        repository.failReads = true
        val alarms = FakeAlarmScheduler()
        val engine = TimerEngine(repository, alarms, FakeReminderNotifier(), FakeClockProvider(), FakeStopIntentStore())
        assertTrue(engine.start().isFailure)
        assertTrue(alarms.scheduledStates.isEmpty())
        repository.failReads = false
        assertEquals(persisted, repository.getTimerState())
    }

    @Test fun notificationCleanupExceptionDoesNotPreventStoppedCommit() = runBlocking {
        val repository = FakeTimerRepository()
        val scheduler = FakeAlarmScheduler()
        val delegate = FakeReminderNotifier()
        var throwOnClear = false
        val notifier = object : ReminderNotifier by delegate {
            override fun clearAllNotifications() {
                if (throwOnClear) throw SecurityException("injected notification cleanup failure")
                delegate.clearAllNotifications()
            }
        }
        val journal = FakeStopIntentStore()
        val engine = TimerEngine(repository, scheduler, notifier, FakeClockProvider(), journal)
        val started = engine.start(1, 1).getOrThrow()
        throwOnClear = true
        val stop = engine.stop()
        assertTrue(stop.isSuccess)
        assertEquals(TimerStatus.STOPPED, engine.getTimerState().status)
        assertEquals(TimerStatus.STOPPED, repository.getTimerState().status)
        assertTrue(scheduler.activeAlarms.isEmpty())
        assertTrue(journal.isRecoveryBlocked(started.sessionId))
    }

    @Test fun scheduleExceptionCommitsAlarmFailure() = runBlocking {
        val repository = FakeTimerRepository()
        val delegate = FakeAlarmScheduler()
        var throwOnSchedule = false
        val scheduler = object : AlarmScheduler by delegate {
            override fun schedulePhaseAlarm(state: TimerState, previousGeneration: Long?): Boolean {
                if (throwOnSchedule) throw SecurityException("injected scheduling failure")
                return delegate.schedulePhaseAlarm(state, previousGeneration)
            }
        }
        val clock = FakeClockProvider()
        val notifier = FakeReminderNotifier()
        val engine = TimerEngine(repository, scheduler, notifier, clock, FakeStopIntentStore())
        val started = engine.start(1, 1).getOrThrow()
        clock.advance(60_000L)
        throwOnSchedule = true
        val transition = engine.onPhaseAlarm(started.sessionId, started.generation, started.phase, started.currentRound)
        assertTrue(transition is PhaseTransitionResult.Failure)
        assertEquals(TimerStatus.ERROR, engine.getTimerState().status)
        assertEquals(engine.getTimerState(), repository.getTimerState())
        assertTrue(delegate.activeAlarms.isEmpty())
        assertTrue(notifier.phaseReminders.isEmpty())
        assertEquals(TimerFailure.ALARM_SCHEDULING, engine.getTimerState().failureReason)
    }

}
