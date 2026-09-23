package com.takeabreak.wearos.notification

import com.takeabreak.wearos.timer.TimerPhase

data class ReminderSelfTestResult(val message: String, val isError: Boolean)

/** Manual diagnostics are separate from the engine's production notification port. */
fun interface ReminderSelfTest {
    fun run(phase: TimerPhase): ReminderSelfTestResult
}

internal interface ReminderSelfTestDevice : ReminderVibrationDevice {
    fun postSilentNotification(phase: TimerPhase)
}

/** A manual motor check. A successful API call is not proof of a physical vibration. */
internal class DefaultReminderSelfTest(private val device: ReminderSelfTestDevice) : ReminderSelfTest {
    override fun run(phase: TimerPhase): ReminderSelfTestResult {
        requestReminderVibration(device, phase).exceptionOrNull()?.let {
            return ReminderSelfTestResult(it.message ?: "振动请求失败", true)
        }

        try {
            device.postSilentNotification(phase)
        } catch (e: Exception) {
            return ReminderSelfTestResult("已请求振动，但测试通知发送失败：${e.message ?: e.javaClass.simpleName}", true)
        }
        val label = if (phase == TimerPhase.BREAK) "休息" else "工作"
        return ReminderSelfTestResult("已请求${label}振动，请确认手表是否有触感；无触感请检查手表振动设置", false)
    }
}
