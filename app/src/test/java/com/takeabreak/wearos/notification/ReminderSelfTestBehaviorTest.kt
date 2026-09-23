package com.takeabreak.wearos.notification

import com.takeabreak.wearos.timer.TimerPhase
import org.junit.Assert.*
import org.junit.Test

class ReminderSelfTestBehaviorTest {
    private class Device : ReminderSelfTestDevice {
        var block: String? = null
        var available = true
        var motorFailure: Exception? = null
        var notificationFailure: Exception? = null
        val calls = mutableListOf<String>()
        override fun blockedReason(phase: TimerPhase) = block
        override fun hasVibrator() = available
        override fun vibrate(phase: TimerPhase) {
            motorFailure?.let { throw it }
            calls += "motor:$phase"
        }
        override fun postSilentNotification(phase: TimerPhase) {
            notificationFailure?.let { throw it }
            calls += "silent:$phase"
        }
    }

    @Test fun restPreviewRequestsMotorBeforeSilentNotification() {
        val device = Device()
        val result = DefaultReminderSelfTest(device).run(TimerPhase.BREAK)
        assertEquals(listOf("motor:BREAK", "silent:BREAK"), device.calls)
        assertFalse(result.isError)
        assertTrue(result.message.contains("请确认"))
    }

    @Test fun repeatedWorkPreviewStillRequestsMotorEveryTime() {
        val device = Device()
        val preview = DefaultReminderSelfTest(device)
        repeat(2) { assertFalse(preview.run(TimerPhase.WORK).isError) }
        assertEquals(listOf("motor:WORK", "silent:WORK", "motor:WORK", "silent:WORK"), device.calls)
    }

    @Test fun notificationOrDndBlockDoesNotBypassUserSettings() {
        val device = Device().apply { block = "当前勿扰模式可能拦截振动" }
        val result = DefaultReminderSelfTest(device).run(TimerPhase.BREAK)
        assertTrue(result.isError)
        assertEquals(device.block, result.message)
        assertTrue(device.calls.isEmpty())
    }

    @Test fun missingMotorReportsFailureWithoutPostingSuccessNotification() {
        val device = Device().apply { available = false }
        val result = DefaultReminderSelfTest(device).run(TimerPhase.WORK)
        assertTrue(result.isError)
        assertTrue(result.message.contains("未检测到"))
        assertTrue(device.calls.isEmpty())
    }

    @Test fun rejectedMotorRequestIsVisibleAndDoesNotClaimSubmitted() {
        val device = Device().apply { motorFailure = SecurityException("VIBRATE denied") }
        val result = DefaultReminderSelfTest(device).run(TimerPhase.BREAK)
        assertTrue(result.isError)
        assertTrue(result.message.contains("VIBRATE denied"))
        assertTrue(device.calls.isEmpty())
    }

    @Test fun notificationFailureDoesNotHideThatMotorWasRequested() {
        val device = Device().apply { notificationFailure = IllegalStateException("notification unavailable") }
        val result = DefaultReminderSelfTest(device).run(TimerPhase.WORK)
        assertTrue(result.isError)
        assertTrue(result.message.contains("已请求振动"))
        assertTrue(result.message.contains("notification unavailable"))
        assertEquals(listOf("motor:WORK"), device.calls)
    }

    @Test fun phaseAlarmRequestsBothPatternsWithoutPostingTestNotification() {
        val device = Device()
        assertTrue(requestReminderVibration(device, TimerPhase.BREAK).isSuccess)
        assertTrue(requestReminderVibration(device, TimerPhase.WORK).isSuccess)
        assertEquals(listOf("motor:BREAK", "motor:WORK"), device.calls)
    }

    @Test fun phaseAlarmRechecksSettingsInsteadOfReusingEarlierPermission() {
        val device = Device()
        assertTrue(requestReminderVibration(device, TimerPhase.BREAK).isSuccess)
        device.block = "该提醒渠道的振动已关闭"
        assertTrue(requestReminderVibration(device, TimerPhase.WORK).isFailure)
        assertEquals(listOf("motor:BREAK"), device.calls)
    }
}
