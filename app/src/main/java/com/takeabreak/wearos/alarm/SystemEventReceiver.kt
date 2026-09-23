package com.takeabreak.wearos.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.takeabreak.wearos.timer.TimerSystemEvent
import com.takeabreak.wearos.TakeABreakApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SystemEventReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val event = when (action) {
            Intent.ACTION_BOOT_COMPLETED -> TimerSystemEvent.BOOT_COMPLETED
            Intent.ACTION_TIME_CHANGED -> TimerSystemEvent.TIME_CHANGED
            Intent.ACTION_TIMEZONE_CHANGED -> TimerSystemEvent.TIMEZONE_CHANGED
            Intent.ACTION_MY_PACKAGE_REPLACED -> TimerSystemEvent.PACKAGE_REPLACED
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED" -> TimerSystemEvent.EXACT_ALARM_PERMISSION_CHANGED
            else -> return
        }
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.Default).launch {
            try {
                val app = context.applicationContext as? TakeABreakApplication
                    ?: TakeABreakApplication.instance
                app.timerEngine.onSystemEvent(event)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
