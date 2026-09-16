package com.takeabreak.wearos.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object NotificationChannels {

    const val CHANNEL_STATUS_ID = "timer_status_v2"
    const val CHANNEL_BREAK_ID = "break_start_v2"
    const val CHANNEL_WORK_ID = "work_start_v2"
    const val CHANNEL_ERROR_ID = "timer_error_v2"

    val BREAK_VIBRATION_PATTERN = longArrayOf(0, 600, 200, 600, 200, 800)
    val WORK_VIBRATION_PATTERN = longArrayOf(0, 300, 200, 300)

    data class ChannelStatusInfo(
        val channelId: String,
        val name: String,
        val exists: Boolean,
        val importance: Int,
        val isVibrationEnabled: Boolean,
        val vibrationPatternString: String
    )

    fun createChannels(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return

        // 1. 状态通知渠道 (低重要性、无提示音、无振动)
        val statusChannel = NotificationChannel(
            CHANNEL_STATUS_ID,
            "计时状态",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "显示当前阶段和剩余时长，不产生振动干扰"
            enableVibration(false)
            setSound(null, null)
            setShowBadge(false)
        }

        // 2. 休息开始提醒渠道 (高重要性、系统原生振动、无声音)
        val breakChannel = NotificationChannel(
            CHANNEL_BREAK_ID,
            "休息开始",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "工作结束提醒休息，使用醒目振动节奏"
            enableVibration(true)
            vibrationPattern = BREAK_VIBRATION_PATTERN
            setSound(null, null) // 不播放默认提示音，仅由渠道触发振动
            setShowBadge(true)
        }

        // 3. 工作开始提醒渠道 (高重要性、系统原生振动、无声音)
        val workChannel = NotificationChannel(
            CHANNEL_WORK_ID,
            "工作开始",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "休息结束提醒工作，使用利落双段振动节奏"
            enableVibration(true)
            vibrationPattern = WORK_VIBRATION_PATTERN
            setSound(null, null)
            setShowBadge(true)
        }

        // 4. 计时异常渠道
        val errorChannel = NotificationChannel(
            CHANNEL_ERROR_ID,
            "计时异常",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "提示调度失效或权限缺失等异常"
            enableVibration(true)
            setShowBadge(true)
        }

        notificationManager.createNotificationChannels(
            listOf(statusChannel, breakChannel, workChannel, errorChannel)
        )
    }

    /**
     * 读取系统实际持久化的渠道配置（不依赖代码假定）
     */
    fun queryChannelStatuses(context: Context): List<ChannelStatusInfo> {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return emptyList()

        val targetChannels = listOf(
            CHANNEL_BREAK_ID to "休息开始",
            CHANNEL_WORK_ID to "工作开始",
            CHANNEL_STATUS_ID to "计时状态",
            CHANNEL_ERROR_ID to "计时异常"
        )

        return targetChannels.map { (id, label) ->
            val channel = notificationManager.getNotificationChannel(id)
            if (channel != null) {
                val pattern = channel.vibrationPattern
                val patternStr = if (pattern != null && pattern.isNotEmpty()) {
                    pattern.joinToString(prefix = "[", postfix = "]") { "${it}ms" }
                } else {
                    if (channel.shouldVibrate()) "系统默认" else "无振动"
                }
                ChannelStatusInfo(
                    channelId = id,
                    name = channel.name?.toString() ?: label,
                    exists = true,
                    importance = channel.importance,
                    isVibrationEnabled = channel.shouldVibrate(),
                    vibrationPatternString = patternStr
                )
            } else {
                ChannelStatusInfo(
                    channelId = id,
                    name = label,
                    exists = false,
                    importance = NotificationManager.IMPORTANCE_NONE,
                    isVibrationEnabled = false,
                    vibrationPatternString = "未创建"
                )
            }
        }
    }
}
