package com.takeabreak.wearos.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.takeabreak.wearos.TakeABreakApplication
import com.takeabreak.wearos.permission.ReminderPermissionChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val sessionId = intent.getStringExtra(NotificationActions.EXTRA_SESSION_ID)

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.Default).launch {
            try {
                val app = context.applicationContext as? TakeABreakApplication
                    ?: TakeABreakApplication.instance

                // 统一委托给 TimerEngine 在互斥锁内进行会话校验、权限检查与状态转移
                // 彻底不直接操作 TimerRepository，杜绝竞态破坏新会话
                NotificationCommandHandler(app.timerEngine).handle(
                    action = action,
                    expectedSessionId = sessionId,
                    preflightChecker = { ReminderPermissionChecker.checkPreflight(app.capabilityReader.read()) }
                )
            } finally {
                pendingResult.finish()
            }
        }
    }
}
