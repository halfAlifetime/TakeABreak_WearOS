package com.takeabreak.wearos.timer.support

import com.takeabreak.wearos.alarm.AlarmScheduler
import com.takeabreak.wearos.notification.ReminderNotifier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import com.takeabreak.wearos.timer.ClockProvider
import com.takeabreak.wearos.timer.TimerRepository
import com.takeabreak.wearos.timer.TimerState
import com.takeabreak.wearos.timer.TimerPhase
import com.takeabreak.wearos.timer.StopIntentStore

class FakeClockProvider(
    var elapsed: Long = 100000L,
    var wall: Long = 1700000000000L
) : ClockProvider {
    var boot: Int? = null
    override fun elapsedRealtime(): Long = elapsed
    override fun currentTimeMillis(): Long = wall
    override fun bootCount(): Int? = boot

    fun advance(ms: Long) {
        elapsed += ms
        wall += ms
    }
}

class FakeTimerRepository : TimerRepository {
    private val stateFlow = MutableStateFlow(TimerState())
    var failWrites: Boolean = false
    var failReads: Boolean = false
    var beforeUpdate: (suspend () -> Unit)? = null
    override val timerStateFlow: Flow<TimerState> = stateFlow

    override suspend fun getTimerState(): TimerState {
        if (failReads) throw java.io.IOException("Fake storage read failure")
        return stateFlow.value
    }

    override suspend fun updateTimerState(transform: (TimerState) -> TimerState): TimerState {
        beforeUpdate?.invoke()
        if (failWrites) {
            throw java.io.IOException("Fake storage write failure")
        }
        val updated = transform(stateFlow.value)
        stateFlow.value = updated
        return updated
    }


}

class FakeStopIntentStore : StopIntentStore {
    var generation: Long = -1L
    var allowedSessionId: String? = null
    var failWrites = false
    var failAllowStart = false
    val stoppedSessions = mutableSetOf<String>()
    override fun stoppedThroughGeneration(): Long = generation
    override fun isRecoveryBlocked(sessionId: String): Boolean =
        sessionId in stoppedSessions || (allowedSessionId?.let { it.isEmpty() || it != sessionId } ?: false)
    override fun markStopped(generation: Long) {
        if (failWrites) throw java.io.IOException("Fake journal failure")
        this.generation = maxOf(this.generation, generation)
        allowedSessionId = ""
        stoppedSessions.clear()
    }
    override fun markSessionStopped(sessionId: String) {
        if (failWrites) throw java.io.IOException("Fake journal failure")
        stoppedSessions.add(sessionId)
    }
    override fun allowStartedSession(sessionId: String) {
        if (failWrites || failAllowStart) throw java.io.IOException("Fake start authorization failure")
        allowedSessionId = sessionId
        stoppedSessions.clear()
    }
}

class FakeAlarmScheduler : AlarmScheduler {
    var canSchedule = true
    var scheduledStates = mutableListOf<TimerState>()
    var cancelledGenerations = mutableListOf<Long>()
    var allAlarmsCancelledCount = 0
    val activeAlarms = mutableMapOf<Long, TimerState>()
    var knowsSessionIdentity = true

    override fun canScheduleExactAlarms(): Boolean = canSchedule

    override fun schedulePhaseAlarm(state: TimerState, previousGeneration: Long?): Boolean {
        if (!canSchedule) return false
        if (previousGeneration != null) {
            cancelledGenerations.add(previousGeneration)
            activeAlarms.remove(previousGeneration)
        }
        scheduledStates.add(state)
        activeAlarms[state.generation] = state
        return true
    }

    override fun cancelPhaseAlarm(generation: Long) {
        cancelledGenerations.add(generation)
        activeAlarms.remove(generation)
    }

    override fun cancelAllPhaseAlarms() {
        allAlarmsCancelledCount++
        activeAlarms.clear()
    }

    override fun cancelSessionAlarms(sessionId: String) {
        if (knowsSessionIdentity) {
            activeAlarms.values.filter { it.sessionId == sessionId }.forEach {
                cancelPhaseAlarm(it.generation)
            }
        }
    }
}

class FakeReminderNotifier : ReminderNotifier {
    var statusShown: TimerState? = null
    val phaseReminders = mutableListOf<String>()
    val errorNotifications = mutableListOf<String>()
    val clearedSessions = mutableListOf<String>()
    private val phaseOwners = mutableMapOf<String, String>()
    private val errorOwners = mutableMapOf<String, String>()

    override fun showStatusNotification(state: TimerState) {
        statusShown = state
    }

    override fun showPhaseReminder(
        phase: TimerPhase,
        round: Int,
        durationMinutes: Int,
        sessionId: String,
        generation: Long
    ) {
        val reminder = "$phase-$round-$durationMinutes-$generation"
        phaseReminders.add(reminder)
        phaseOwners[reminder] = sessionId
    }

    override fun showErrorNotification(message: String, sessionId: String) {
        errorNotifications.add(message)
        errorOwners[message] = sessionId
    }

    override fun clearStatusNotification() {
        statusShown = null
    }

    override fun clearReminderNotifications() {
        phaseReminders.clear()
        errorNotifications.clear()
        phaseOwners.clear()
        errorOwners.clear()
    }

    override fun clearAllNotifications() {
        statusShown = null
        clearReminderNotifications()
    }

    override fun clearSessionNotifications(sessionId: String) {
        clearedSessions.add(sessionId)
        if (statusShown?.sessionId == sessionId) statusShown = null
        phaseReminders.removeAll { phaseOwners[it] == sessionId }
        errorNotifications.removeAll { errorOwners[it] == sessionId }
    }

}
