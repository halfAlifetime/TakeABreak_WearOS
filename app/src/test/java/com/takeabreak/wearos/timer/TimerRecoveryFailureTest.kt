package com.takeabreak.wearos.timer

import com.takeabreak.wearos.timer.support.FakeAlarmScheduler
import com.takeabreak.wearos.timer.support.FakeClockProvider
import com.takeabreak.wearos.timer.support.FakeReminderNotifier
import com.takeabreak.wearos.timer.support.FakeStopIntentStore
import com.takeabreak.wearos.timer.support.FakeTimerRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.util.logging.Handler
import java.util.logging.LogRecord
import java.util.logging.Logger
import org.junit.Assert.*
import org.junit.Test

class TimerRecoveryFailureTest {
    private val repository = FakeTimerRepository()
    private val scheduler = FakeAlarmScheduler()
    private val notifier = FakeReminderNotifier()
    private val clock = FakeClockProvider()
    private val journal = FakeStopIntentStore()
    private fun newEngine() = TimerEngine(repository, scheduler, notifier, clock, journal)
    private val engine = newEngine()

    @Test fun expiredRecoveryFailurePublishesErrorAndLogsOriginalCause() = runBlocking {
        val started = engine.start().getOrThrow()
        clock.advance(3_600_001L)
        val failure = IOException("Recovery storage unavailable")
        repository.beforeUpdate = { throw failure }
        val logger = Logger.getLogger(TimerEngine::class.java.name)
        val records = mutableListOf<LogRecord>()
        val handler = object : Handler() {
            override fun publish(record: LogRecord) { records += record }
            override fun flush() = Unit
            override fun close() = Unit
        }
        logger.addHandler(handler)
        try {
            val failed = engine.onSystemEvent(TimerSystemEvent.TIME_CHANGED)
            assertEquals(TimerStatus.ERROR, failed.status)
            assertEquals(TimerFailure.STORAGE_WRITE, failed.failureReason)
            assertEquals(started.phase, failed.phase)
            assertEquals(started.currentRound, failed.currentRound)
            assertEquals(0L, failed.pausedRemainingMs)
            assertEquals(0L, failed.deadlineElapsedRealtimeMs)
            assertEquals(0L, failed.deadlineWallClockMs)
            assertEquals(failed, engine.timerStateFlow.first())
            assertEquals(failed, notifier.statusShown)
            assertTrue(scheduler.activeAlarms.isEmpty())
            assertTrue(notifier.phaseReminders.isEmpty())
            // All writes failed, including the fallback. The old snapshot still exists.
            assertEquals(started, repository.getTimerState())
            assertEquals(2, records.size)
            records.forEach {
                assertSame(failure, it.thrown)
                assertTrue(it.message.contains("session=${failed.sessionId}"))
                assertTrue(it.message.contains("generation=${failed.generation}"))
            }

            repository.beforeUpdate = null
            val retried = engine.retry().getOrThrow()
            assertEquals(TimerPhase.BREAK, retried.phase)
            assertEquals(1, retried.currentRound)
            assertEquals(300_000L, retried.phaseTotalDurationMs)
            assertNull(retried.failureReason)
            assertEquals(retried, repository.getTimerState())
        } finally {
            logger.removeHandler(handler)
        }
    }

    @Test fun fallbackFaultSurvivesRecreationAndWaitsForExplicitRetry() = runBlocking {
        engine.start().getOrThrow()
        clock.advance(3_600_001L)
        var writes = 0
        repository.beforeUpdate = {
            writes++
            if (writes <= 2) throw IOException("Initial recovery writes failed")
        }
        val failed = engine.onSystemEvent(TimerSystemEvent.TIME_CHANGED)
        assertEquals(3, writes)
        assertEquals(TimerStatus.ERROR, failed.status)
        assertEquals(TimerFailure.STORAGE_WRITE, failed.failureReason)
        assertEquals(failed, repository.getTimerState())
        repository.beforeUpdate = null

        val recreated = newEngine()
        assertEquals(failed, recreated.getTimerState())
        assertEquals(failed, recreated.onSystemEvent(TimerSystemEvent.EXACT_ALARM_PERMISSION_CHANGED))
        assertTrue(scheduler.activeAlarms.isEmpty())
        val resumed = recreated.retry().getOrThrow()
        assertEquals(TimerStatus.RUNNING, resumed.status)
        assertEquals(TimerPhase.BREAK, resumed.phase)
        assertEquals(1, resumed.currentRound)
        assertEquals(300_000L, resumed.phaseTotalDurationMs)
        assertEquals(listOf(resumed), scheduler.activeAlarms.values.toList())
        assertTrue(notifier.phaseReminders.isEmpty())
    }

    @Test fun unsavedExpiredRecoveryCanBeReconciledAfterRecreation() = runBlocking {
        val started = engine.start().getOrThrow()
        clock.advance(3_600_001L)
        repository.failWrites = true
        assertEquals(TimerFailure.STORAGE_WRITE, engine.onSystemEvent(TimerSystemEvent.TIME_CHANGED).failureReason)
        assertEquals(started, repository.getTimerState())
        repository.failWrites = false

        // With no successful write, recreation alone cannot recover the lost fault.
        // A subsequent system event must reconcile the stored RUNNING snapshot safely.
        val recreated = newEngine()
        val reconciled = recreated.onSystemEvent(TimerSystemEvent.TIME_CHANGED)
        assertEquals(TimerStatus.PAUSED, reconciled.status)
        assertEquals(0L, reconciled.pausedRemainingMs)
        assertEquals(reconciled, repository.getTimerState())
        assertTrue(scheduler.activeAlarms.isEmpty())
        assertTrue(notifier.phaseReminders.isEmpty())
    }

    @Test fun unexpiredRecoveryFailurePreservesRemainingTimeForRetry() = runBlocking {
        engine.start().getOrThrow()
        clock.advance(1_200_000L)
        repository.failWrites = true
        val failed = engine.onSystemEvent(TimerSystemEvent.TIME_CHANGED)
        assertEquals(TimerStatus.ERROR, failed.status)
        assertEquals(TimerFailure.STORAGE_WRITE, failed.failureReason)
        assertEquals(TimerPhase.WORK, failed.phase)
        assertEquals(2_400_000L, failed.pausedRemainingMs)
        assertEquals(failed, notifier.statusShown)
        assertTrue(scheduler.activeAlarms.isEmpty())
        repository.failWrites = false
        val retried = engine.retry().getOrThrow()
        assertEquals(TimerPhase.WORK, retried.phase)
        assertEquals(2_400_000L, retried.calculateRemainingMs(clock.elapsed))
    }

    @Test fun fallbackCancellationPropagatesWithoutLosingEffectiveFault() = runBlocking {
        engine.start().getOrThrow()
        clock.advance(3_600_001L)
        val cancellation = CancellationException("Fallback persistence cancelled")
        var writes = 0
        repository.beforeUpdate = {
            writes++
            if (writes <= 2) throw IOException("Recovery writes failed")
            throw cancellation
        }
        val thrown = try {
            engine.onSystemEvent(TimerSystemEvent.TIME_CHANGED)
            null
        } catch (error: CancellationException) {
            error
        }
        assertNotNull(thrown)
        assertEquals(cancellation.message, thrown?.message)
        // Propagation across Dispatchers.IO may add a coroutine stack-trace wrapper.
        assertTrue(generateSequence<Throwable>(thrown) { it.cause }.any { it === cancellation })
        assertEquals(3, writes)
        assertEquals(TimerFailure.STORAGE_WRITE, engine.getTimerState().failureReason)
        assertTrue(scheduler.activeAlarms.isEmpty())
    }
}
