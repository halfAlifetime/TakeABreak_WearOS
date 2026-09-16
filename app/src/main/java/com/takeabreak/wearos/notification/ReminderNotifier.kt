package com.takeabreak.wearos.notification

import android.app.Notification
import android.media.AudioAttributes
import android.os.Build
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import com.takeabreak.wearos.MainActivity
import com.takeabreak.wearos.R
import com.takeabreak.wearos.timer.TimerPhase
import com.takeabreak.wearos.timer.TimerState
import com.takeabreak.wearos.timer.TimerStatus
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale

interface ReminderNotifier {
    fun showStatusNotification(state: TimerState)
    fun showPhaseReminder(
        phase: TimerPhase,
        round: Int,
        durationMinutes: Int,
        sessionId: String,
        generation: Long
    )
    fun showErrorNotification(message: String, sessionId: String)
    fun clearStatusNotification()
    fun clearReminderNotifications()
    fun clearAllNotifications()
    fun sendTestReminder(phase: TimerPhase)
}

class AndroidReminderNotifier(private val context: Context) : ReminderNotifier {

    companion object {
        const val NOTIFICATION_ID_STATUS = 1001
        const val NOTIFICATION_ID_ERROR = 1002
        const val NOTIFICATION_ID_TEST = 1003
        const val NOTIFICATION_ID_REMINDER = 2000

        const val ACTION_PAUSE = "com.takeabreak.wearos.action.PAUSE"
        const val ACTION_RESUME = "com.takeabreak.wearos.action.RESUME"
        const val ACTION_STOP = "com.takeabreak.wearos.action.STOP"
        const val EXTRA_SESSION_ID = "extra_session_id"
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    private fun wakeScreen() {
        runCatching {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            @Suppress("DEPRECATION")
            val screenLock = powerManager?.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
                "TakeABreak:NotifierScreenWake"
            )
            screenLock?.acquire(4000L)
        }
    }

    private fun triggerHaptic(phase: TimerPhase) {
        runCatching {
            val pattern = when (phase) {
                TimerPhase.BREAK -> NotificationChannels.BREAK_VIBRATION_PATTERN
                TimerPhase.WORK -> NotificationChannels.WORK_VIBRATION_PATTERN
            }
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.vibrate(
                    VibrationEffect.createWaveform(pattern, -1),
                    audioAttributes
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(
                        VibrationEffect.createWaveform(pattern, -1),
                        audioAttributes
                    )
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(pattern, -1)
                }
            }
        }
    }


    // 记录已发布的带 Tag 提醒通知，确保 stop 或启动新循环时彻底清空
    private val activeReminderTags = Collections.synchronizedSet(mutableSetOf<String>())

    override fun showStatusNotification(state: TimerState) {
        if (notificationManager == null) return
        if (state.status == TimerStatus.STOPPED) {
            clearStatusNotification()
            return
        }

        val openAppIntent = MainActivity.createOpenTimerIntent(context)
        val openPendingIntent = PendingIntent.getActivity(
            context,
            1,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = when (state.phase) {
            TimerPhase.WORK -> "工作中 · 第 ${state.currentRound} 轮"
            TimerPhase.BREAK -> "休息中 · 第 ${state.currentRound} 轮"
        }

        val content = when (state.status) {
            TimerStatus.RUNNING -> {
                val endTimeStr = if (state.deadlineWallClockMs > 0L) {
                    timeFormat.format(Date(state.deadlineWallClockMs))
                } else "--:--"
                "预计 $endTimeStr 结束"
            }
            TimerStatus.PAUSED -> "已暂停 · 剩余 ${state.formattedRemainingTime(0L)}"
            TimerStatus.ERROR -> "计时中断：${state.errorMessage ?: "未知异常"}"
            TimerStatus.STOPPED -> "已停止"
        }

        val isRunning = state.status == TimerStatus.RUNNING

        val builder = NotificationCompat.Builder(context, NotificationChannels.CHANNEL_STATUS_ID)
            .setSmallIcon(R.drawable.ic_ongoing_timer)
            .setContentTitle(title)
            .setContentText(content)
            .setContentIntent(openPendingIntent)
            .setOngoing(isRunning)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        // 核心：若处于 RUNNING 状态，每一次重新构建并发布状态通知都必须附带 Wear OS Ongoing Activity 元数据
        // 确保在手表表盘恢复持续活动小图标，点击后直接返回当前真实计时页，不重新 start()，不重置时间
        if (isRunning) {
            val ongoingStatus = Status.Builder()
                .addTemplate(content)
                .build()

            val ongoingActivity = OngoingActivity.Builder(context, NOTIFICATION_ID_STATUS, builder)
                .setStaticIcon(R.drawable.ic_ongoing_timer)
                .setTouchIntent(openPendingIntent)
                .setStatus(ongoingStatus)
                .setTitle(title)
                .build()

            ongoingActivity.apply(context)

            // 添加操作按钮：暂停
            val pauseIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                action = ACTION_PAUSE
                putExtra(EXTRA_SESSION_ID, state.sessionId)
            }
            val pausePending = PendingIntent.getBroadcast(
                context,
                201,
                pauseIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(android.R.drawable.ic_media_pause, "暂停", pausePending)
        } else if (state.status == TimerStatus.PAUSED) {
            // PAUSED 状态：撤下“运行中”持续活动入口，保留常驻普通暂停通知，提供继续
            val resumeIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                action = ACTION_RESUME
                putExtra(EXTRA_SESSION_ID, state.sessionId)
            }
            val resumePending = PendingIntent.getBroadcast(
                context,
                202,
                resumeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(android.R.drawable.ic_media_play, "继续", resumePending)
        }

        val stopIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = ACTION_STOP
            putExtra(EXTRA_SESSION_ID, state.sessionId)
        }
        val stopPending = PendingIntent.getBroadcast(
            context,
            203,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "停止", stopPending)

        notificationManager.notify(NOTIFICATION_ID_STATUS, builder.build())
    }

    override fun showPhaseReminder(
        phase: TimerPhase,
        round: Int,
        durationMinutes: Int,
        sessionId: String,
        generation: Long
    ) {
        if (notificationManager == null) return

        val (channelId, title, message) = when (phase) {
            TimerPhase.BREAK -> Triple(
                NotificationChannels.CHANNEL_BREAK_ID,
                "该休息了",
                "休息 $durationMinutes 分钟，稍后提醒你继续工作"
            )
            TimerPhase.WORK -> Triple(
                NotificationChannels.CHANNEL_WORK_ID,
                "休息结束",
                "开始第 $round 轮工作 ($durationMinutes 分钟)"
            )
        }

        val openAppIntent = MainActivity.createOpenTimerIntent(context)
        val openPendingIntent = PendingIntent.getActivity(
            context,
            2,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 使用会话与 generation 生成唯一 tag，确保每轮到点提醒均能独立振动触发
        val tag = "phase_${sessionId}_${generation}"

        // 清理旧的阶段提醒，防止表盘下拉通知栏堆积过时消息
        synchronized(activeReminderTags) {
            for (oldTag in activeReminderTags) {
                notificationManager.cancel(oldTag, NOTIFICATION_ID_REMINDER)
            }
            activeReminderTags.clear()
            activeReminderTags.add(tag)
        }

        // 触发硬件立即亮屏与强震动，避开 Wear OS / 三星手表息屏下的通知静默与抬腕延迟
        wakeScreen()
        triggerHaptic(phase)

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setContentIntent(openPendingIntent)
            .setFullScreenIntent(openPendingIntent, true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        notificationManager.notify(tag, NOTIFICATION_ID_REMINDER, builder.build())
    }

    override fun showErrorNotification(message: String, sessionId: String) {
        if (notificationManager == null) return

        val openAppIntent = MainActivity.createOpenTimerIntent(context)
        val openPendingIntent = PendingIntent.getActivity(
            context,
            3,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, NotificationChannels.CHANNEL_ERROR_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("计时已暂停")
            .setContentText(message)
            .setContentIntent(openPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        notificationManager.notify(NOTIFICATION_ID_ERROR, builder.build())
    }

    override fun clearStatusNotification() {
        notificationManager?.cancel(NOTIFICATION_ID_STATUS)
    }

    override fun clearReminderNotifications() {
        val nm = notificationManager ?: return
        synchronized(activeReminderTags) {
            for (tag in activeReminderTags) {
                nm.cancel(tag, NOTIFICATION_ID_REMINDER)
            }
            activeReminderTags.clear()
        }
        // 扫描已发布通知，清理历史提醒及错误通知，严格保护 NOTIFICATION_ID_STATUS (1001) 状态与表盘小图标
        runCatching {
            val activeNotifs = nm.activeNotifications
            for (sbn in activeNotifs) {
                if (sbn.id == NOTIFICATION_ID_REMINDER || sbn.id == NOTIFICATION_ID_ERROR || sbn.tag?.startsWith("phase_") == true) {
                    nm.cancel(sbn.tag, sbn.id)
                }
            }
        }
    }

    override fun clearAllNotifications() {
        clearStatusNotification()
        clearReminderNotifications()
        notificationManager?.cancel(NOTIFICATION_ID_TEST)
    }

    override fun sendTestReminder(phase: TimerPhase) {
        if (notificationManager == null) return

        val (channelId, title, message) = when (phase) {
            TimerPhase.BREAK -> Triple(
                NotificationChannels.CHANNEL_BREAK_ID,
                "【测试】该休息了",
                "正在调用【休息开始】系统通知渠道执行振动"
            )
            TimerPhase.WORK -> Triple(
                NotificationChannels.CHANNEL_WORK_ID,
                "【测试】休息结束",
                "正在调用【工作开始】系统通知渠道执行振动"
            )
        }

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            99,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        wakeScreen()
        triggerHaptic(phase)

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setContentIntent(openPendingIntent)
            .setFullScreenIntent(openPendingIntent, true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)

        notificationManager.notify(NOTIFICATION_ID_TEST, builder.build())
    }
}
