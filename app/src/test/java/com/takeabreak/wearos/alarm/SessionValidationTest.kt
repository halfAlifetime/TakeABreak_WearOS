package com.takeabreak.wearos.alarm

import com.takeabreak.wearos.timer.FakeAlarmScheduler
import com.takeabreak.wearos.timer.FakeClockProvider
import com.takeabreak.wearos.timer.FakeReminderNotifier
import com.takeabreak.wearos.timer.FakeTimerRepository
import com.takeabreak.wearos.timer.PhaseTransitionResult
import com.takeabreak.wearos.timer.TimerEngine
import com.takeabreak.wearos.timer.TimerPhase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SessionValidationTest {

    private lateinit var clock: FakeClockProvider
    private lateinit var repo: FakeTimerRepository
    private lateinit var scheduler: FakeAlarmScheduler
    private lateinit var notifier: FakeReminderNotifier
    private lateinit var engine: TimerEngine

    @Before
    fun setup() {
        clock = FakeClockProvider()
        repo = FakeTimerRepository()
        scheduler = FakeAlarmScheduler()
        notifier = FakeReminderNotifier()
        engine = TimerEngine(repo, scheduler, notifier, clock)
    }

    @Test
    fun testMismatchSessionId_IsSafelyIgnored() = runBlocking {
        engine.start(customWorkMin = 60, customBreakMin = 5)
        val state = repo.getTimerState()

        val result = engine.onPhaseAlarm(
            sessionId = "wrong-session-id-12345",
            generation = state.generation,
            expectedPhase = TimerPhase.WORK,
            round = 1
        )

        assertTrue(result is PhaseTransitionResult.Ignored)
    }

    @Test
    fun testPrematureAlarm_ReschedulesAndDoesNotSwitchPhase() = runBlocking {
        engine.start(customWorkMin = 60, customBreakMin = 5)
        val state = repo.getTimerState()

        // 闹钟提前 5 分钟到达
        clock.advance(55 * 60 * 1000L) // 离 60 分钟还剩 5 分钟

        val result = engine.onPhaseAlarm(
            sessionId = state.sessionId,
            generation = state.generation,
            expectedPhase = TimerPhase.WORK,
            round = 1
        )

        assertTrue(result is PhaseTransitionResult.Ignored)
        // 确保依然停留在 WORK 阶段
        val currentState = repo.getTimerState()
        assertTrue(currentState.phase == TimerPhase.WORK)
    }
}
