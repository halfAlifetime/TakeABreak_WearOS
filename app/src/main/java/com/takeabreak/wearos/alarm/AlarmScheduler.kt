package com.takeabreak.wearos.alarm

import com.takeabreak.wearos.timer.TimerState

/** System port. Calls may throw; TimerSystemEffects contains failures for engine transactions. */
interface AlarmScheduler {
    fun canScheduleExactAlarms(): Boolean
    fun schedulePhaseAlarm(state: TimerState, previousGeneration: Long? = null): Boolean
    fun cancelPhaseAlarm(generation: Long)
    fun cancelSessionAlarms(sessionId: String)
    fun cancelAllPhaseAlarms()
}
