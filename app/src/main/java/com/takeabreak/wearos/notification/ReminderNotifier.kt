package com.takeabreak.wearos.notification

import com.takeabreak.wearos.permission.ReminderCapabilityReader
import android.os.Build
import android.os.Bundle
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import com.takeabreak.wearos.MainActivity
import com.takeabreak.wearos.R
import com.takeabreak.wearos.timer.TimerPhase
import com.takeabreak.wearos.timer.TimerState
import com.takeabreak.wearos.timer.TimerStatus
import java.text.SimpleDateFormat
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
    fun clearSessionNotifications(sessionId: String)
    fun sendTestReminder(phase: TimerPhase): ReminderTestResult
}

class AndroidReminderNotifier(
    private val context: Context,
    capabilityReader: ReminderCapabilityReader
) : ReminderNotifier {

    companion object {
        const val NOTIFICATION_ID_STATUS = 1001
        const val NOTIFICATION_ID_ERROR = 1002
        const val NOTIFICATION_ID_TEST = 1003
        const val NOTIFICATION_ID_REMINDER = 2000

        const val ACTION_PAUSE = NotificationActions.PAUSE
        const val ACTION_RESUME = NotificationActions.RESUME
        const val ACTION_STOP = NotificationActions.STOP
        const val EXTRA_SESSION_ID = NotificationActions.EXTRA_SESSION_ID
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val vibration = AndroidReminderVibration(context, capabilityReader)

    private fun canUseFullScreenIntent(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            notificationManager?.canUseFullScreenIntent() == true

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
            .addExtras(Bundle().apply { putString(EXTRA_SESSION_ID, state.sessionId) })
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
                data = Uri.parse("takeabreak://notification/${state.sessionId}")
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
                data = Uri.parse("takeabreak://notification/${state.sessionId}")
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
            data = Uri.parse("takeabreak://notification/${state.sessionId}")
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

        // Query the system so notifications from an earlier process are removed too.
        clearReminderNotifications()

        val builder = NotificationCompat.Builder(context, channelId)
            .addExtras(Bundle().apply { putString(EXTRA_SESSION_ID, sessionId) })
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setContentIntent(openPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        if (canUseFullScreenIntent()) builder.setFullScreenIntent(openPendingIntent, true)
        // Wear OS notification delivery can succeed without a motor request. Use the
        // verified alarm vibration path after the engine has committed the new phase.
        requestReminderVibration(vibration, phase).onFailure {
            Log.w("ReminderVibration", "Phase vibration unavailable: phase=$phase", it)
        }
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
            .addExtras(Bundle().apply { putString(EXTRA_SESSION_ID, sessionId) })
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

    override fun clearSessionNotifications(sessionId: String) {
        val nm = notificationManager ?: return
        runCatching {
            for (notification in nm.activeNotifications) {
                if (notification.notification.extras.getString(EXTRA_SESSION_ID) == sessionId ||
                    notification.tag?.startsWith("phase_${sessionId}_") == true
                ) {
                    nm.cancel(notification.tag, notification.id)
                }
            }
        }
    }

    override fun clearAllNotifications() {
        vibration.cancel()
        clearStatusNotification()
        clearReminderNotifications()
        notificationManager?.cancel(NOTIFICATION_ID_TEST)
    }

    override fun sendTestReminder(phase: TimerPhase): ReminderTestResult =
        ReminderVibrationTest(object : ReminderTestDevice {
            override fun blockedReason(phase: TimerPhase) = vibration.blockedReason(phase)
            override fun hasVibrator() = vibration.hasVibrator()
            override fun vibrate(phase: TimerPhase) = vibration.vibrate(phase)
            override fun postSilentNotification(phase: TimerPhase) = postTestNotification(phase)
        }).run(phase)

    private fun postTestNotification(phase: TimerPhase) {
        val manager = checkNotNull(notificationManager) { "无法访问系统通知服务" }

        val (channelId, title, message) = when (phase) {
            TimerPhase.BREAK -> Triple(
                NotificationChannels.CHANNEL_BREAK_ID,
                "【测试】该休息了",
                "已请求休息振动自检，请以手表实际触感为准"
            )
            TimerPhase.WORK -> Triple(
                NotificationChannels.CHANNEL_WORK_ID,
                "【测试】休息结束",
                "已请求工作振动自检，请以手表实际触感为准"
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

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setContentIntent(openPendingIntent)
            // The explicit motor test owns haptics; do not play a second channel vibration
            // or navigate away from the test feedback with a full-screen notification.
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)

        manager.notify(NOTIFICATION_ID_TEST, builder.build())
    }
}
