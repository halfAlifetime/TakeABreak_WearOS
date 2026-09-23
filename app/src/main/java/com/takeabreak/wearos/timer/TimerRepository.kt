package com.takeabreak.wearos.timer

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.timerDataStore: DataStore<Preferences> by preferencesDataStore(name = "take_a_break_timer_prefs")

interface TimerRepository {
    val timerStateFlow: Flow<TimerState>
    suspend fun getTimerState(): TimerState
    suspend fun updateTimerState(transform: (TimerState) -> TimerState): TimerState
    suspend fun saveDurations(workMinutes: Int, breakMinutes: Int)
}

class DataStoreTimerRepository(private val context: Context) : TimerRepository {

    private object PreferencesKeys {
        val SESSION_ID = stringPreferencesKey("session_id")
        val GENERATION = longPreferencesKey("generation")
        val STATUS = stringPreferencesKey("status")
        val PHASE = stringPreferencesKey("phase")
        val CURRENT_ROUND = intPreferencesKey("current_round")
        val WORK_DURATION_MIN = intPreferencesKey("work_duration_min")
        val BREAK_DURATION_MIN = intPreferencesKey("break_duration_min")
        val PHASE_TOTAL_DURATION_MS = longPreferencesKey("phase_total_duration_ms")
        val DEADLINE_ELAPSED_MS = longPreferencesKey("deadline_elapsed_ms")
        val DEADLINE_WALL_MS = longPreferencesKey("deadline_wall_ms")
        val PAUSED_REMAINING_MS = longPreferencesKey("paused_remaining_ms")
        val BOOT_IDENTIFIER = longPreferencesKey("boot_identifier")
        val BOOT_COUNT = intPreferencesKey("boot_count")
        val LAST_EVENT_ID = stringPreferencesKey("last_event_id")
        val LAST_EVENT_RESULT = stringPreferencesKey("last_event_result")
        val LAST_EVENT_TIMESTAMP_MS = longPreferencesKey("last_event_timestamp_ms")
        val ERROR_MESSAGE = stringPreferencesKey("error_message")
    }

    override val timerStateFlow: Flow<TimerState> = context.timerDataStore.data.map { prefs ->
        mapPreferencesToTimerState(prefs)
    }

    override suspend fun getTimerState(): TimerState {
        val prefs = context.timerDataStore.data.first()
        return mapPreferencesToTimerState(prefs)
    }

    override suspend fun updateTimerState(transform: (TimerState) -> TimerState): TimerState {
        var updatedState = TimerState()
        context.timerDataStore.edit { prefs ->
            val currentState = mapPreferencesToTimerState(prefs)
            updatedState = transform(currentState)
            prefs[PreferencesKeys.SESSION_ID] = updatedState.sessionId
            prefs[PreferencesKeys.GENERATION] = updatedState.generation
            prefs[PreferencesKeys.STATUS] = updatedState.status.name
            prefs[PreferencesKeys.PHASE] = updatedState.phase.name
            prefs[PreferencesKeys.CURRENT_ROUND] = updatedState.currentRound
            prefs[PreferencesKeys.WORK_DURATION_MIN] = updatedState.workDurationMinutes
            prefs[PreferencesKeys.BREAK_DURATION_MIN] = updatedState.breakDurationMinutes
            prefs[PreferencesKeys.PHASE_TOTAL_DURATION_MS] = updatedState.phaseTotalDurationMs
            prefs[PreferencesKeys.DEADLINE_ELAPSED_MS] = updatedState.deadlineElapsedRealtimeMs
            prefs[PreferencesKeys.DEADLINE_WALL_MS] = updatedState.deadlineWallClockMs
            prefs[PreferencesKeys.PAUSED_REMAINING_MS] = updatedState.pausedRemainingMs
            prefs[PreferencesKeys.BOOT_IDENTIFIER] = updatedState.bootIdentifier
            updatedState.bootCount?.let { prefs[PreferencesKeys.BOOT_COUNT] = it }
                ?: prefs.remove(PreferencesKeys.BOOT_COUNT)
            prefs[PreferencesKeys.LAST_EVENT_ID] = updatedState.lastEventId
            prefs[PreferencesKeys.LAST_EVENT_RESULT] = updatedState.lastEventResult
            prefs[PreferencesKeys.LAST_EVENT_TIMESTAMP_MS] = updatedState.lastEventTimestampMs
            if (updatedState.errorMessage != null) {
                prefs[PreferencesKeys.ERROR_MESSAGE] = updatedState.errorMessage!!
            } else {
                prefs.remove(PreferencesKeys.ERROR_MESSAGE)
            }
        }
        return updatedState
    }

    override suspend fun saveDurations(workMinutes: Int, breakMinutes: Int) {
        context.timerDataStore.edit { prefs ->
            prefs[PreferencesKeys.WORK_DURATION_MIN] = workMinutes
            prefs[PreferencesKeys.BREAK_DURATION_MIN] = breakMinutes
        }
    }

    private fun mapPreferencesToTimerState(prefs: Preferences): TimerState {
        val statusStr = prefs[PreferencesKeys.STATUS] ?: TimerStatus.STOPPED.name
        val phaseStr = prefs[PreferencesKeys.PHASE] ?: TimerPhase.WORK.name
        val workMin = prefs[PreferencesKeys.WORK_DURATION_MIN] ?: 60
        val breakMin = prefs[PreferencesKeys.BREAK_DURATION_MIN] ?: 5

        return TimerState(
            sessionId = prefs[PreferencesKeys.SESSION_ID] ?: "",
            generation = prefs[PreferencesKeys.GENERATION] ?: 0L,
            status = runCatching { TimerStatus.valueOf(statusStr) }.getOrDefault(TimerStatus.STOPPED),
            phase = runCatching { TimerPhase.valueOf(phaseStr) }.getOrDefault(TimerPhase.WORK),
            currentRound = prefs[PreferencesKeys.CURRENT_ROUND] ?: 1,
            workDurationMinutes = workMin,
            breakDurationMinutes = breakMin,
            phaseTotalDurationMs = prefs[PreferencesKeys.PHASE_TOTAL_DURATION_MS] ?: (workMin * 60 * 1000L),
            deadlineElapsedRealtimeMs = prefs[PreferencesKeys.DEADLINE_ELAPSED_MS] ?: 0L,
            deadlineWallClockMs = prefs[PreferencesKeys.DEADLINE_WALL_MS] ?: 0L,
            pausedRemainingMs = prefs[PreferencesKeys.PAUSED_REMAINING_MS] ?: 0L,
            bootIdentifier = prefs[PreferencesKeys.BOOT_IDENTIFIER] ?: 0L,
            bootCount = prefs[PreferencesKeys.BOOT_COUNT],
            lastEventId = prefs[PreferencesKeys.LAST_EVENT_ID] ?: "",
            lastEventResult = prefs[PreferencesKeys.LAST_EVENT_RESULT] ?: "",
            lastEventTimestampMs = prefs[PreferencesKeys.LAST_EVENT_TIMESTAMP_MS] ?: 0L,
            errorMessage = prefs[PreferencesKeys.ERROR_MESSAGE]
        )
    }
}
