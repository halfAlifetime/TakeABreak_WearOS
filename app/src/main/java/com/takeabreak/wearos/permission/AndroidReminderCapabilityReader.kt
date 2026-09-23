package com.takeabreak.wearos.permission

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.takeabreak.wearos.alarm.AlarmScheduler
import com.takeabreak.wearos.notification.NotificationChannels

class AndroidReminderCapabilityReader(
    private val context: Context,
    private val scheduler: AlarmScheduler
) : ReminderCapabilityReader {
    override fun read(): ReminderCapabilities {
        val manager = readOrNull { context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager }
        return ReminderCapabilities(
            exactAlarmsAllowed = readOrNull { scheduler.canScheduleExactAlarms() },
            notificationsEnabled = readOrNull { NotificationManagerCompat.from(context).areNotificationsEnabled() },
            postNotificationsGranted = readOrNull {
                Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            },
            workChannel = readChannel(manager, NotificationChannels.CHANNEL_WORK_ID),
            breakChannel = readChannel(manager, NotificationChannels.CHANNEL_BREAK_ID),
            statusChannel = readChannel(manager, NotificationChannels.CHANNEL_STATUS_ID),
            interruptionMode = readOrNull {
                when (manager?.currentInterruptionFilter) {
                    NotificationManager.INTERRUPTION_FILTER_ALL -> InterruptionMode.ALL
                    NotificationManager.INTERRUPTION_FILTER_ALARMS -> InterruptionMode.ALARMS
                    NotificationManager.INTERRUPTION_FILTER_PRIORITY -> InterruptionMode.PRIORITY
                    NotificationManager.INTERRUPTION_FILTER_NONE -> InterruptionMode.NONE
                    else -> InterruptionMode.UNKNOWN
                }
            } ?: InterruptionMode.UNKNOWN,
            vibratorAvailable = readOrNull {
                val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    (context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
                }
                vibrator?.hasVibrator() ?: false
            },
            fullScreenIntentAllowed = if (manager == null) null else readOrNull {
                Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE || manager.canUseFullScreenIntent()
            }
        )
    }

    private fun readChannel(manager: NotificationManager?, id: String): ChannelCapability {
        if (manager == null) return ChannelCapability()
        return readOrNull {
            val channel = manager.getNotificationChannel(id)
            if (channel == null) ChannelCapability(exists = false) else ChannelCapability(
                exists = true,
                importance = when (channel.importance) {
                    NotificationManager.IMPORTANCE_NONE -> ChannelImportance.NONE
                    NotificationManager.IMPORTANCE_MIN -> ChannelImportance.MIN
                    NotificationManager.IMPORTANCE_LOW -> ChannelImportance.LOW
                    NotificationManager.IMPORTANCE_DEFAULT -> ChannelImportance.DEFAULT
                    NotificationManager.IMPORTANCE_HIGH, NotificationManager.IMPORTANCE_MAX -> ChannelImportance.HIGH
                    else -> ChannelImportance.UNKNOWN
                },
                vibrationEnabled = channel.shouldVibrate(),
                groupBlocked = if (channel.group == null) false else manager.getNotificationChannelGroup(channel.group)?.isBlocked
            )
        } ?: ChannelCapability()
    }

    private inline fun <T> readOrNull(block: () -> T): T? = try { block() } catch (_: Exception) { null }
}
