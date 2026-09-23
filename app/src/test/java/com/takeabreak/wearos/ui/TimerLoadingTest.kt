package com.takeabreak.wearos.ui

import androidx.lifecycle.ViewModelStore
import com.takeabreak.wearos.notification.ReminderSelfTestResult
import com.takeabreak.wearos.permission.ReminderCapabilityReader
import com.takeabreak.wearos.permission.support.readyCapabilities
import com.takeabreak.wearos.timer.TimerEngine
import com.takeabreak.wearos.timer.TimerPhase
import com.takeabreak.wearos.timer.TimerRepository
import com.takeabreak.wearos.timer.TimerState
import com.takeabreak.wearos.timer.TimerStatus
import com.takeabreak.wearos.timer.support.FakeAlarmScheduler
import com.takeabreak.wearos.timer.support.FakeClockProvider
import com.takeabreak.wearos.timer.support.FakeReminderNotifier
import com.takeabreak.wearos.timer.support.FakeStopIntentStore
import com.takeabreak.wearos.timer.support.FakeTimerRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TimerLoadingTest {
    @Test fun coldLoadingCannotStartOverPausedSession() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        val persisted = FakeTimerRepository()
        val paused = TimerState(
            sessionId = "existing-paused-session", generation = 12,
            status = TimerStatus.PAUSED, phase = TimerPhase.BREAK, currentRound = 3,
            phaseTotalDurationMs = 300_000L, pausedRemainingMs = 90_000L
        )
        persisted.updateTimerState { paused }
        val readEntered = CompletableDeferred<Unit>()
        val releaseRead = CompletableDeferred<Unit>()
        val repository = object : TimerRepository by persisted {
            override suspend fun getTimerState(): TimerState {
                readEntered.complete(Unit)
                releaseRead.await()
                return persisted.getTimerState()
            }
        }
        val engine = TimerEngine(repository, FakeAlarmScheduler(), FakeReminderNotifier(), FakeClockProvider(), FakeStopIntentStore())
        val vm = TimerViewModel(engine, FakeClockProvider(), ReminderCapabilityReader { readyCapabilities() }) {
            ReminderSelfTestResult("unused", false)
        }
        store.put("probe", vm)
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.timerState.collect() }
        try {
            readEntered.await()
            assertNull(vm.timerState.value)
            val start = vm.startTimer()
            runCurrent()
            assertTrue(start.isCompleted)
            // Loading blocks every state-changing control, including duration edits.
            val commands = listOf(vm.pauseTimer(), vm.resumeTimer(), vm.retryTimer(), vm.stopTimer(),
                vm.setWorkDuration(45), vm.setBreakDuration(10))
            runCurrent()
            assertTrue(commands.all { it.isCompleted })
            releaseRead.complete(Unit)
            start.join()
            runCurrent()
            assertEquals(paused, vm.timerState.value)
            // Even a stale callback after loading must be rejected by the engine.
            vm.startTimer().join()
            assertEquals(FeedbackType.ERROR, vm.feedback.value?.type)
            val saved = persisted.getTimerState()
            assertEquals(paused, saved)
            assertEquals(TimerStatus.PAUSED, saved.status)
            assertEquals(paused.sessionId, saved.sessionId)
            assertEquals(TimerPhase.BREAK, saved.phase)
            assertEquals(3, saved.currentRound)
            assertEquals(90_000L, saved.pausedRemainingMs)
        } finally {
            releaseRead.complete(Unit)
            collectJob.cancel()
            store.clear()
            runCurrent()
            Dispatchers.resetMain()
        }
    }
}
