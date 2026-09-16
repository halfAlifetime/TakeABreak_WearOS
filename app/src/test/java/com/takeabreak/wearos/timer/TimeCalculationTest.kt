package com.takeabreak.wearos.timer

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeCalculationTest {

    @Test
    fun testRemainingTimeAndFormatting() {
        val nowElapsed = 500000L
        val totalMs = 60 * 60 * 1000L // 60 分钟
        val deadline = nowElapsed + 3540000L // 剩余 59 分钟整

        val state = TimerState(
            status = TimerStatus.RUNNING,
            phaseTotalDurationMs = totalMs,
            deadlineElapsedRealtimeMs = deadline
        )

        val remainingMs = state.calculateRemainingMs(nowElapsed)
        assertEquals(3540000L, remainingMs)
        assertEquals("59:00", state.formattedRemainingTime(nowElapsed))

        // 测试进度比例
        val progress = state.calculateProgress(nowElapsed)
        assertEquals(3540000f / totalMs.toFloat(), progress, 0.001f)
    }

    @Test
    fun testZeroAndNegativeRemainingTime_ClampedToZero() {
        val nowElapsed = 500000L
        val deadline = nowElapsed - 10000L // 已过期 10 秒

        val state = TimerState(
            status = TimerStatus.RUNNING,
            phaseTotalDurationMs = 60000L,
            deadlineElapsedRealtimeMs = deadline
        )

        assertEquals(0L, state.calculateRemainingMs(nowElapsed))
        assertEquals("00:00", state.formattedRemainingTime(nowElapsed))
        assertEquals(0f, state.calculateProgress(nowElapsed), 0.001f)
    }

    @Test
    fun testPausedRemainingTime() {
        val state = TimerState(
            status = TimerStatus.PAUSED,
            pausedRemainingMs = 45000L,
            phaseTotalDurationMs = 60000L
        )

        assertEquals(45000L, state.calculateRemainingMs(999999L))
        assertEquals("00:45", state.formattedRemainingTime(999999L))
        assertEquals(0.75f, state.calculateProgress(999999L), 0.001f)
    }
}
