package com.takeabreak.wearos.notification

import com.takeabreak.wearos.timer.TimerPhase

internal interface ReminderVibrationDevice {
    fun blockedReason(phase: TimerPhase): String?
    fun hasVibrator(): Boolean
    fun vibrate(phase: TimerPhase)
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
