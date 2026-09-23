package com.takeabreak.wearos.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import com.takeabreak.wearos.TakeABreakApplication
import com.takeabreak.wearos.timer.TimerPhase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AndroidAlarmScheduler.ACTION_PHASE_ALARM) {
            return
        }

        val sessionId = intent.getStringExtra(AndroidAlarmScheduler.EXTRA_SESSION_ID) ?: return
        val generation = intent.getLongExtra(AndroidAlarmScheduler.EXTRA_GENERATION, -1L)
        if (generation < 0L) return

        val phaseName = intent.getStringExtra(AndroidAlarmScheduler.EXTRA_EXPECTED_PHASE) ?: return
        val expectedPhase = runCatching { TimerPhase.valueOf(phaseName) }.getOrNull() ?: return
        val round = intent.getIntExtra(AndroidAlarmScheduler.EXTRA_ROUND, 1)

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "TakeABreak:AlarmReceiverWakeLock"
        )?.apply {
            setReferenceCounted(false)
            acquire(5000L)
        }

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.Default).launch {
            try {
                val app = context.applicationContext as? TakeABreakApplication
                    ?: TakeABreakApplication.instance
                app.timerEngine.onPhaseAlarm(
                    sessionId = sessionId,
                    generation = generation,
                    expectedPhase = expectedPhase,
                    round = round
                )
            } finally {
                try {
                    if (wakeLock?.isHeld == true) {
                        wakeLock.release()
                    }
                } catch (_: Exception) {
                }
                pendingResult.finish()
            }
        }
    }
}
