package com.takeabreak.wearos.permission

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.takeabreak.wearos.notification.NotificationChannels

data class PermissionEvaluation(
    val areNotificationsEnabled: Boolean,
    val hasPostNotificationsPermission: Boolean,
    val canScheduleExactAlarms: Boolean,
    val isBreakChannelReady: Boolean,
    val isWorkChannelReady: Boolean,
    val breakVibrationEnabled: Boolean,
    val workVibrationEnabled: Boolean,
    val isStatusChannelReady: Boolean,
    val statusChannelImportance: Int,
    val isDndSuppressed: Boolean,
    val summaryMessage: String
) {
    val isFullyOperational: Boolean
        get() = areNotificationsEnabled &&
                hasPostNotificationsPermission &&
                canScheduleExactAlarms &&
                isBreakChannelReady &&
                isWorkChannelReady &&
                breakVibrationEnabled &&
                workVibrationEnabled
}

enum class SettingTarget {
    NONE,
    EXACT_ALARM,
    APP_NOTIFICATION,
    CHANNEL_WORK,
    CHANNEL_BREAK,
    CHANNEL_STATUS
}

sealed class PreflightCheckResult {
    object Passed : PreflightCheckResult()
    data class Blocked(
        val reason: String,
        val target: SettingTarget
    ) : PreflightCheckResult()
}

object ReminderPermissionChecker {

    /**
     * 统一命令前置提醒能力检查（供 UI 启动/继续/重试、通知操作广播及系统恢复共用）
     * 区分渠道关闭与优先级降低，只有真正阻止提醒的核心缺失才会 Blocked
     */
    fun checkPreflight(context: Context): PreflightCheckResult {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        val canScheduleExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val hasUseExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, "android.permission.USE_EXACT_ALARM") == PackageManager.PERMISSION_GRANTED
            } else false
            hasUseExact || (alarmManager?.canScheduleExactAlarms() ?: false)
        } else {
            true
        }
        if (!canScheduleExact) {
            return PreflightCheckResult.Blocked(
                reason = "精确闹钟权限未开启，无法保证准时提醒",
                target = SettingTarget.EXACT_ALARM
            )
        }

        val notificationManagerCompat = NotificationManagerCompat.from(context)
        if (!notificationManagerCompat.areNotificationsEnabled()) {
            return PreflightCheckResult.Blocked(
                reason = "通知总开关已关闭，无法接收到点提醒",
                target = SettingTarget.APP_NOTIFICATION
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPostPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasPostPermission) {
                return PreflightCheckResult.Blocked(
                    reason = "未授予应用通知权限，无法接收到点提醒",
                    target = SettingTarget.APP_NOTIFICATION
                )
            }
        }

        val systemNotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        if (systemNotificationManager != null) {
            NotificationChannels.createChannels(context)

            val workChannel = systemNotificationManager.getNotificationChannel(NotificationChannels.CHANNEL_WORK_ID)
            if (workChannel != null) {
                if (workChannel.importance == NotificationManager.IMPORTANCE_NONE) {
                    return PreflightCheckResult.Blocked(
                        reason = "工作提醒渠道已关闭，无法发送提醒",
                        target = SettingTarget.CHANNEL_WORK
                    )
                }
                if (!workChannel.shouldVibrate()) {
                    return PreflightCheckResult.Blocked(
                        reason = "工作提醒渠道振动已关闭，无触感提醒",
                        target = SettingTarget.CHANNEL_WORK
                    )
                }
            }

            val breakChannel = systemNotificationManager.getNotificationChannel(NotificationChannels.CHANNEL_BREAK_ID)
            if (breakChannel != null) {
                if (breakChannel.importance == NotificationManager.IMPORTANCE_NONE) {
                    return PreflightCheckResult.Blocked(
                        reason = "休息提醒渠道已关闭，无法发送提醒",
                        target = SettingTarget.CHANNEL_BREAK
                    )
                }
                if (!breakChannel.shouldVibrate()) {
                    return PreflightCheckResult.Blocked(
                        reason = "休息提醒渠道振动已关闭，无触感提醒",
                        target = SettingTarget.CHANNEL_BREAK
                    )
                }
            }
        }

        return PreflightCheckResult.Passed
    }

    fun evaluate(context: Context): PermissionEvaluation {
        val notificationManagerCompat = NotificationManagerCompat.from(context)
        val notificationsEnabled = notificationManagerCompat.areNotificationsEnabled()

        // Android 13 (API 33) 显式运行时通知权限检查
        val hasPostNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        val canScheduleExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val hasUseExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, "android.permission.USE_EXACT_ALARM") == PackageManager.PERMISSION_GRANTED
            } else false
            hasUseExact || (alarmManager?.canScheduleExactAlarms() ?: false)
        } else {
            true
        }

        val systemNotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

        // 若渠道尚未初始化，自动补充创建
        if (systemNotificationManager != null) {
            NotificationChannels.createChannels(context)
        }

        var breakReady = false
        var breakVibrate = false
        var workReady = false
        var workVibrate = false
        var statusChannelReady = false
        var statusChannelImportance = 0
        var isDndSuppressed = false

        if (systemNotificationManager != null) {
            val breakChannel = systemNotificationManager.getNotificationChannel(NotificationChannels.CHANNEL_BREAK_ID)
            if (breakChannel != null) {
                breakReady = breakChannel.importance >= NotificationManager.IMPORTANCE_HIGH
                breakVibrate = breakChannel.shouldVibrate()
            }

            val workChannel = systemNotificationManager.getNotificationChannel(NotificationChannels.CHANNEL_WORK_ID)
            if (workChannel != null) {
                workReady = workChannel.importance >= NotificationManager.IMPORTANCE_HIGH
                workVibrate = workChannel.shouldVibrate()
            }

            // 单独检查状态通知渠道（用于表盘持续活动小图标与常驻状态栏）
            val statusChannel = systemNotificationManager.getNotificationChannel(NotificationChannels.CHANNEL_STATUS_ID)
            if (statusChannel != null) {
                statusChannelImportance = statusChannel.importance
                statusChannelReady = statusChannel.importance > NotificationManager.IMPORTANCE_NONE
            }

            // 检查勿扰模式是否完全拦截警报/提醒
            val filter = systemNotificationManager.currentInterruptionFilter
            isDndSuppressed = (filter == NotificationManager.INTERRUPTION_FILTER_NONE)
        }

        val summary = when {
            !notificationsEnabled -> "系统通知总开关未开启，无法接收振动提醒"
            !hasPostNotificationPermission -> "未授予应用通知权限 (Android 13+)，请允许通知"
            !canScheduleExact -> "精确闹钟权限未开启，无法保证息屏准时提醒"
            !breakReady || !workReady -> "提醒渠道重要性低于高优先级，可能无法弹屏提醒"
            !breakVibrate || !workVibrate -> "通知渠道振动未开启，将无触感提醒"
            !statusChannelReady -> "状态通知渠道被关闭，表盘持续活动小图标将无法显示"
            isDndSuppressed -> "手表当前处于全禁勿扰模式，所有提醒可能被静音"
            else -> "调度与通知配置已就绪，请通过振动自检确认实际触感"
        }

        return PermissionEvaluation(
            areNotificationsEnabled = notificationsEnabled,
            hasPostNotificationsPermission = hasPostNotificationPermission,
            canScheduleExactAlarms = canScheduleExact,
            isBreakChannelReady = breakReady,
            isWorkChannelReady = workReady,
            breakVibrationEnabled = breakVibrate,
            workVibrationEnabled = workVibrate,
            isStatusChannelReady = statusChannelReady,
            statusChannelImportance = statusChannelImportance,
            isDndSuppressed = isDndSuppressed,
            summaryMessage = summary
        )
    }

    fun createAppNotificationSettingsIntent(context: Context): Intent {
        return Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    fun createExactAlarmSettingsIntent(context: Context): Intent {
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

    fun createStatusChannelSettingsIntent(context: Context): Intent {
        return createChannelSettingsIntent(context, NotificationChannels.CHANNEL_STATUS_ID)
    }

    fun createChannelSettingsIntent(context: Context, channelId: String): Intent {
        return Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }
}
