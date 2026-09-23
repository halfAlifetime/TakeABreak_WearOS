package com.takeabreak.wearos.notification

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.takeabreak.wearos.timer.TimerPhase

internal class AndroidReminderVibration(private val context: Context) : ReminderVibrationDevice {
    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            (context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
        }
    }

    override fun blockedReason(phase: TimerPhase): String? {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled() ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
        ) return "应用通知未开启，请在手表通知设置中允许“休息一下”的通知"

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return "无法访问系统通知服务"
        NotificationChannels.createChannels(context)
        val channelId = if (phase == TimerPhase.BREAK) NotificationChannels.CHANNEL_BREAK_ID else NotificationChannels.CHANNEL_WORK_ID
        val channel = manager.getNotificationChannel(channelId) ?: return "提醒渠道未创建，请重新打开应用"
        if (channel.importance < NotificationManager.IMPORTANCE_DEFAULT) return "该提醒渠道已关闭或静音，请在通知设置中恢复提醒"
        if (!channel.shouldVibrate()) return "该提醒渠道的振动已关闭，请先在通知设置中开启"
        if (channel.group?.let { manager.getNotificationChannelGroup(it)?.isBlocked } == true) {
            return "该提醒所属通知分组已关闭，请先在通知设置中开启"
        }
        // Do not bypass a user's restricted interruption mode with the direct alarm vibration.
        val filter = manager.currentInterruptionFilter
        if (filter != NotificationManager.INTERRUPTION_FILTER_ALL &&
            filter != NotificationManager.INTERRUPTION_FILTER_ALARMS
        ) return "当前勿扰模式可能拦截振动，请在勿扰设置中确认"
        return null
    }

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
