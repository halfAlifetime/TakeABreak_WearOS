package com.takeabreak.wearos.notification

import com.takeabreak.wearos.timer.TimerPhase

data class ReminderTestResult(val message: String, val isError: Boolean)

internal interface ReminderVibrationDevice {
    fun blockedReason(phase: TimerPhase): String?
    fun hasVibrator(): Boolean
    fun vibrate(phase: TimerPhase)
}

internal interface ReminderTestDevice : ReminderVibrationDevice {
    fun postSilentNotification(phase: TimerPhase)
}

/** Shared by manual tests and actual phase alarms; respects the current user settings. */
internal fun requestReminderVibration(device: ReminderVibrationDevice, phase: TimerPhase): Result<Unit> {
    return try {
        device.blockedReason(phase)?.let { return Result.failure(IllegalStateException(it)) }
        if (!device.hasVibrator()) return Result.failure(IllegalStateException("未检测到可用的手表振动器"))
        device.vibrate(phase)
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(IllegalStateException("振动请求失败：${e.message ?: e.javaClass.simpleName}", e))
    }
}

/** A manual motor check. A successful API call is not proof of a physical vibration. */
internal class ReminderVibrationTest(private val device: ReminderTestDevice) {
    fun run(phase: TimerPhase): ReminderTestResult {
        requestReminderVibration(device, phase).exceptionOrNull()?.let {
            return ReminderTestResult(it.message ?: "振动请求失败", true)
        }

        try {
            device.postSilentNotification(phase)
        } catch (e: Exception) {
            return ReminderTestResult("已请求振动，但测试通知发送失败：${e.message ?: e.javaClass.simpleName}", true)
        }
        val label = if (phase == TimerPhase.BREAK) "休息" else "工作"
        return ReminderTestResult("已请求${label}振动，请确认手表是否有触感；无触感请检查手表振动设置", false)
    }
}
