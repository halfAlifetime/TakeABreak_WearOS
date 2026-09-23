package com.takeabreak.wearos.notification

/** Stable wire protocol shared by notification PendingIntents and their entry-point decoder. */
object NotificationActions {
    const val PAUSE = "com.takeabreak.wearos.action.PAUSE"
    const val RESUME = "com.takeabreak.wearos.action.RESUME"
    const val STOP = "com.takeabreak.wearos.action.STOP"
    const val EXTRA_SESSION_ID = "extra_session_id"
}
