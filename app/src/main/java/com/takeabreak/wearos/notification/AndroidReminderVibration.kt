package com.takeabreak.wearos.notification

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.takeabreak.wearos.timer.TimerPhase
import com.takeabreak.wearos.permission.ReminderCapabilityReader
import com.takeabreak.wearos.permission.ReminderMessages
import com.takeabreak.wearos.permission.ReminderPolicy

internal class AndroidReminderVibration(
    private val context: Context,
    private val capabilityReader: ReminderCapabilityReader
) : ReminderVibrationDevice {
    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            (context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
        }
    }

    override fun blockedReason(phase: TimerPhase): String? =
        ReminderPolicy.vibration(capabilityReader.read(), phase).blocker?.let(ReminderMessages::describe)

    override fun hasVibrator(): Boolean = vibrator?.hasVibrator() == true

    override fun vibrate(phase: TimerPhase) {
        val motor = checkNotNull(vibrator) { "无法访问手表振动器" }
        val pattern = if (phase == TimerPhase.BREAK) NotificationChannels.BREAK_VIBRATION_PATTERN else NotificationChannels.WORK_VIBRATION_PATTERN
        val effect = VibrationEffect.createWaveform(pattern, -1)
        try {
            motor.cancel()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                motor.vibrate(effect, VibrationAttributes.Builder().setUsage(VibrationAttributes.USAGE_ALARM).build())
            } else {
                @Suppress("DEPRECATION")
                motor.vibrate(effect, AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_ALARM).build())
            }
            Log.i("ReminderVibration", "Vibration requested: phase=$phase, durationMs=${pattern.sum()}")
        } catch (e: Exception) {
            Log.w("ReminderVibration", "Vibration request failed: phase=$phase", e)
            throw e
        }
    }

    fun cancel() {
        runCatching { vibrator?.cancel() }
            .onFailure { Log.w("ReminderVibration", "Could not cancel vibration", it) }
    }
}
