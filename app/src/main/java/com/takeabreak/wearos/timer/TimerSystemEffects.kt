package com.takeabreak.wearos.timer

import com.takeabreak.wearos.alarm.AlarmScheduler
import com.takeabreak.wearos.notification.ReminderNotifier
import kotlinx.coroutines.CancellationException
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Contains failures from system ports without interrupting the engine's state commit.
 * Scheduling failures enter the engine's ALARM_SCHEDULING path. Cleanup and notification
 * failures are logged; session/generation checks still invalidate undelivered old events.
 * This boundary owns no timer state and must be called within the engine transaction.
 */
internal class TimerSystemEffects(
    private val scheduler: AlarmScheduler,
    private val notifier: ReminderNotifier
) {
    private val logger = Logger.getLogger(TimerSystemEffects::class.java.name)

    fun canScheduleExactAlarms(): Boolean =
        attempt("read exact alarm capability") { scheduler.canScheduleExactAlarms() }.getOrDefault(false)

    fun schedulePhaseAlarm(state: TimerState, previousGeneration: Long? = null): Boolean =
        attempt("schedule session=${state.sessionId} generation=${state.generation}") {
            scheduler.schedulePhaseAlarm(state, previousGeneration)
        }.getOrDefault(false)

    fun cancelPhaseAlarm(generation: Long) {
        attempt("cancel generation=$generation") { scheduler.cancelPhaseAlarm(generation) }
    }

    fun cancelSessionAlarms(sessionId: String) {
        attempt("cancel session=$sessionId") { scheduler.cancelSessionAlarms(sessionId) }
    }

    fun cancelAllPhaseAlarms() {
        attempt("cancel all phase alarms") { scheduler.cancelAllPhaseAlarms() }
    }

    fun showStatusNotification(state: TimerState) {
        attempt("show status=${state.status} session=${state.sessionId}") { notifier.showStatusNotification(state) }
    }

    fun showPhaseReminder(phase: TimerPhase, round: Int, durationMinutes: Int, sessionId: String, generation: Long) {
        attempt("show phase=$phase session=$sessionId generation=$generation") {
            notifier.showPhaseReminder(phase, round, durationMinutes, sessionId, generation)
        }
    }

    fun showErrorNotification(message: String, sessionId: String) {
        attempt("show error session=$sessionId") { notifier.showErrorNotification(message, sessionId) }
    }

    fun clearReminderNotifications() {
        attempt("clear reminder notifications") { notifier.clearReminderNotifications() }
    }

    fun clearAllNotifications() {
        attempt("clear all notifications") { notifier.clearAllNotifications() }
    }

    fun clearSessionNotifications(sessionId: String) {
        attempt("clear notifications session=$sessionId") { notifier.clearSessionNotifications(sessionId) }
    }

    private inline fun <T> attempt(operation: String, block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        logger.log(Level.WARNING, "System operation failed: $operation", error)
        Result.failure(error)
    }
}
