package com.takeabreak.wearos.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.takeabreak.wearos.MainActivity
import com.takeabreak.wearos.timer.ClockProvider
import com.takeabreak.wearos.timer.TimerState

class AndroidAlarmScheduler(
    private val context: Context,
    private val clockProvider: ClockProvider
) : AlarmScheduler {

    companion object {
        const val ACTION_PHASE_ALARM = "com.takeabreak.wearos.ACTION_PHASE_ALARM"
        const val EXTRA_SESSION_ID = "extra_session_id"
        const val EXTRA_GENERATION = "extra_generation"
        const val EXTRA_EXPECTED_PHASE = "extra_expected_phase"
        const val EXTRA_ROUND = "extra_round"

        private const val REQUEST_CODE_BASE = 10000
        private const val PREFS_NAME = "take_a_break_alarm_scheduler"
        private const val KEY_LAST_SCHEDULED_GEN = "last_scheduled_generation"
        private const val KEY_LAST_SCHEDULED_SESSION = "last_scheduled_session"
    }

    private val alarmManager: AlarmManager? =
        context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private var lastScheduledGeneration: Long?
        get() {
            val gen = prefs.getLong(KEY_LAST_SCHEDULED_GEN, -1L)
            return if (gen >= 0L) gen else null
        }
        set(value) {
            prefs.edit().apply {
                if (value != null) putLong(KEY_LAST_SCHEDULED_GEN, value)
                else {
                    remove(KEY_LAST_SCHEDULED_GEN)
                    remove(KEY_LAST_SCHEDULED_SESSION)
                }
            }.apply()
        }

    override fun canScheduleExactAlarms(): Boolean {
        if (alarmManager == null) return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val hasUseExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    "android.permission.USE_EXACT_ALARM"
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            } else false
            hasUseExact || alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    override fun schedulePhaseAlarm(state: TimerState, previousGeneration: Long?): Boolean {
        if (alarmManager == null) return false
        if (!canScheduleExactAlarms()) {
            return false
        }

        val nowElapsed = clockProvider.elapsedRealtime()
        val remainingMs = (state.deadlineElapsedRealtimeMs - nowElapsed).coerceAtLeast(0L)
        if (remainingMs <= 0L) {
            return false
        }

        // 1. 严格清理既往闹钟：优先取消传入的旧 generation、上次记录的 generation、以及相近的最近代次
        val prevGen = previousGeneration ?: lastScheduledGeneration
        if (prevGen != null && prevGen != state.generation) {
            cancelPhaseAlarm(prevGen)
        }
        // 范围清理前 5 代，防止多轮变更累积孤儿 PendingIntent
        for (g in (state.generation - 5) until state.generation) {
            if (g >= 0L) {
                cancelPhaseAlarm(g)
            }
        }

        // 2. 计算正确的 Wall Clock 触发时刻 (符合 AlarmClockInfo 要求)
        val triggerWallMs = clockProvider.currentTimeMillis() + remainingMs

        val showIntent = MainActivity.createOpenTimerIntent(context)
        val showPendingIntent = PendingIntent.getActivity(
            context,
            0,
            showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerWallMs, showPendingIntent)

        val alarmIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_PHASE_ALARM
            putExtra(EXTRA_SESSION_ID, state.sessionId)
            putExtra(EXTRA_GENERATION, state.generation)
            putExtra(EXTRA_EXPECTED_PHASE, state.phase.name)
            putExtra(EXTRA_ROUND, state.currentRound)
        }

        val requestCode = (REQUEST_CODE_BASE + (state.generation % 10000)).toInt()
        val alarmPendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Unexpected failures must reach TimerSystemEffects with their original cause.
        // Only a known unavailable capability or expired deadline returns false.
        if (canScheduleExactAlarms()) {
            try {
                alarmManager.setAlarmClock(alarmClockInfo, alarmPendingIntent)
            } catch (alarmClockFailure: SecurityException) {
                try {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        state.deadlineElapsedRealtimeMs,
                        alarmPendingIntent
                    )
                } catch (fallbackFailure: Exception) {
                    fallbackFailure.addSuppressed(alarmClockFailure)
                    throw fallbackFailure
                }
            }
        } else {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                state.deadlineElapsedRealtimeMs,
                alarmPendingIntent
            )
        }
        prefs.edit()
            .putLong(KEY_LAST_SCHEDULED_GEN, state.generation)
            .putString(KEY_LAST_SCHEDULED_SESSION, state.sessionId)
            .apply()
        return true
    }

    override fun cancelPhaseAlarm(generation: Long) {
        if (alarmManager == null || generation < 0L) return
        val alarmIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_PHASE_ALARM
        }
        val requestCode = (REQUEST_CODE_BASE + (generation % 10000)).toInt()
        val alarmPendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            alarmIntent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (alarmPendingIntent != null) {
            alarmManager.cancel(alarmPendingIntent)
            alarmPendingIntent.cancel()
        }
        if (lastScheduledGeneration == generation) {
            lastScheduledGeneration = null
        }
    }

    override fun cancelSessionAlarms(sessionId: String) {
        // A stale notification must not cancel the alarm owned by a newer session.
        if (prefs.getString(KEY_LAST_SCHEDULED_SESSION, null) == sessionId) {
            lastScheduledGeneration?.let { cancelPhaseAlarm(it) }
        }
    }

    override fun cancelAllPhaseAlarms() {
        val last = lastScheduledGeneration
        if (last != null) {
            for (g in (last - 10)..last) {
                if (g >= 0L) cancelPhaseAlarm(g)
            }
            lastScheduledGeneration = null
        }
    }
}
