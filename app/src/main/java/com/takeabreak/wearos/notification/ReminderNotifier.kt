package com.takeabreak.wearos.notification

import com.takeabreak.wearos.timer.TimerPhase
import com.takeabreak.wearos.timer.TimerState

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
    /** Clears all owned notifications and cancels any active reminder vibration. */
    fun clearAllNotifications()
    /** Clears only this session; must not cancel a newer session's vibration. */
    fun clearSessionNotifications(sessionId: String)
}
