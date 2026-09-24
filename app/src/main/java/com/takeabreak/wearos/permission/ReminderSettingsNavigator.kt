package com.takeabreak.wearos.permission

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.takeabreak.wearos.notification.NotificationChannels
import kotlinx.coroutines.CancellationException

object ReminderSettingsNavigator {
    /** Covers intent construction and launch, including OEM-specific settings failures. */
    fun open(context: Context, target: SettingTarget): Result<Unit> = try {
        val intent = when (target) {
            SettingTarget.EXACT_ALARM -> createExactAlarmSettingsIntent(context)
            SettingTarget.APP_NOTIFICATION -> createAppNotificationSettingsIntent(context)
            SettingTarget.CHANNEL_WORK -> createChannelSettingsIntent(context, NotificationChannels.CHANNEL_WORK_ID)
            SettingTarget.CHANNEL_BREAK -> createChannelSettingsIntent(context, NotificationChannels.CHANNEL_BREAK_ID)
            SettingTarget.CHANNEL_STATUS -> createChannelSettingsIntent(context, NotificationChannels.CHANNEL_STATUS_ID)
            SettingTarget.NONE -> null
        }
        if (intent != null) context.startActivity(intent)
        Result.success(Unit)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Log.w("ReminderSettings", "Could not open settings: target=$target", error)
        Result.failure(error)
    }

    private fun createAppNotificationSettingsIntent(context: Context): Intent {
        return Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    private fun createExactAlarmSettingsIntent(context: Context): Intent {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val exactAlarmIntent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val resolveInfo = context.packageManager.resolveActivity(exactAlarmIntent, 0)
            if (resolveInfo != null) {
                return exactAlarmIntent
            }
        }
        // 兼容回退：若手表系统裁剪了单独的精确闹钟页面，跳转到应用详情设置页
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    private fun createChannelSettingsIntent(context: Context, channelId: String): Intent {
        return Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }
}
