package com.takeabreak.wearos.timer

import com.takeabreak.wearos.timer.support.FakeReminderNotifier
import com.takeabreak.wearos.timer.support.FakeAlarmScheduler
import com.takeabreak.wearos.timer.support.FakeStopIntentStore
import com.takeabreak.wearos.timer.support.FakeTimerRepository
import com.takeabreak.wearos.timer.support.FakeClockProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class DurationUpdateTest {
    private val repo = FakeTimerRepository()
    private val engine = TimerEngine(repo, FakeAlarmScheduler(), FakeReminderNotifier(), FakeClockProvider(), FakeStopIntentStore())

    private fun queuedEdits(workFirst: Boolean) = runBlocking {
        withTimeout(5000) {
            engine.getTimerState()
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            repo.beforeUpdate = {
                repo.beforeUpdate = null
                entered.complete(Unit)
                release.await()
            }
            // The second command queues while the first save is suspended.
            suspend fun edit(work: Boolean) = if (work) {
                engine.updateWorkDuration(45)
            } else {
                engine.updateBreakDuration(10)
            }
            val first = async(start = CoroutineStart.UNDISPATCHED) { edit(workFirst) }
            entered.await()
            val second = async(start = CoroutineStart.UNDISPATCHED) { edit(!workFirst) }
            release.complete(Unit)
            first.await().getOrThrow()
            second.await().getOrThrow()
            val final = engine.getTimerState()
            assertEquals(45, final.workDurationMinutes)
            assertEquals(10, final.breakDurationMinutes)
            assertEquals(45 * 60_000L, final.phaseTotalDurationMs)
            assertEquals(final, repo.getTimerState())
        }
    }

    @Test fun queuedWorkThenBreakKeepsBothChanges() = queuedEdits(true)
    @Test fun queuedBreakThenWorkKeepsBothChanges() = queuedEdits(false)

    @Test fun lastSuccessfulWorkEditWinsWithoutChangingBreak() = runBlocking {
        engine.updateBreakDuration(10).getOrThrow()
        engine.updateWorkDuration(45).getOrThrow()
        engine.updateWorkDuration(90).getOrThrow()
        assertEquals(90, engine.getTimerState().workDurationMinutes)
        assertEquals(10, engine.getTimerState().breakDurationMinutes)
    }

    @Test fun queuedDurationRechecksStatusAfterStartCommits() = runBlocking {
        withTimeout(5000) {
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            repo.beforeUpdate = {
                repo.beforeUpdate = null
                entered.complete(Unit)
                release.await()
            }
            val started = async(start = CoroutineStart.UNDISPATCHED) { engine.start() }
            entered.await()
            val change = async(start = CoroutineStart.UNDISPATCHED) { engine.updateBreakDuration(10) }
            release.complete(Unit)
            started.await().getOrThrow()
            assertTrue(change.await().isFailure)
            assertEquals(TimerStatus.RUNNING, engine.getTimerState().status)
            assertEquals(5, engine.getTimerState().breakDurationMinutes)
        }
    }

    @Test fun invalidSingleFieldDoesNotChangeEitherPreference() = runBlocking {
        val before = engine.getTimerState()
        for (minutes in listOf(0, -1)) {
            assertTrue(engine.updateWorkDuration(minutes).isFailure)
            assertTrue(engine.updateBreakDuration(minutes).isFailure)
        }
        assertEquals(before, engine.getTimerState())
        assertEquals(before, repo.getTimerState())
    }

    @Test fun failedWriteNeverPublishesUnsavedPreference() = runBlocking {
        val before = engine.getTimerState()
        repo.failWrites = true
        assertTrue(engine.updateWorkDuration(45).isFailure)
        assertTrue(engine.updateBreakDuration(10).isFailure)
        assertEquals(before, engine.getTimerState())
        assertEquals(before, repo.getTimerState())
    }
}
