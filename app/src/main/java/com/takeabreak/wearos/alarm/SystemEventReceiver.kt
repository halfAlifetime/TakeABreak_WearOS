package com.takeabreak.wearos.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.takeabreak.wearos.TakeABreakApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SystemEventReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.Default).launch {
            try {
                val app = context.applicationContext as? TakeABreakApplication
                    ?: TakeABreakApplication.instance
                app.timerEngine.onSystemEvent(action)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
