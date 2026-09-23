package com.takeabreak.wearos.timer

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Real file-backed DataStore, including closing/reopening; no fake persistence codec. */
class TimerPersistenceTest {
    @get:Rule val temp = TemporaryFolder()

    private suspend fun withStore(file: File, block: suspend (DataStore<Preferences>, DataStoreTimerRepository) -> Unit) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
        try {
            block(store, DataStoreTimerRepository(store))
        } finally {
            scope.cancel()
            scope.coroutineContext.job.join()
        }
    }

    @Test fun legacyErrorWithoutCodeAndUnknownFutureCodeLoadConservatively() = runBlocking {
        for (code in listOf(null, "future_failure")) {
            val file = File(temp.newFolder(), "timer.preferences_pb")
            withStore(file) { store, _ ->
                store.edit {
                    it[stringPreferencesKey("status")] = "ERROR"
                    it[stringPreferencesKey("session_id")] = "legacy"
                    it[stringPreferencesKey("error_message")] = "旧闹钟权限错误"
                    it[intPreferencesKey("work_duration_min")] = 45
                    it[intPreferencesKey("break_duration_min")] = 10
                    if (code != null) it[stringPreferencesKey("failure_reason")] = code
                }
            }
            withStore(file) { _, repo ->
                val state = repo.getTimerState()
                assertEquals(TimerFailure.UNKNOWN, state.failureReason)
                assertEquals("legacy", state.sessionId)
                assertEquals(45, state.workDurationMinutes)
                assertEquals(10, state.breakDurationMinutes)
            }
        }
    }

    @Test fun stableFailureCodesSurviveFileReopenAndAreRemovedOnStop() = runBlocking {
        for (failure in TimerFailure.entries) {
            val file = File(temp.newFolder(), "timer.preferences_pb")
            withStore(file) { store, repo ->
                repo.updateTimerState { it.copy(sessionId = "new", status = TimerStatus.ERROR,
                    failureReason = failure, errorMessage = "diagnostic only") }
                assertEquals(failure.storageCode, store.data.first()[stringPreferencesKey("failure_reason")])
            }
            withStore(file) { store, repo ->
                assertEquals(failure, repo.getTimerState().failureReason)
                repo.updateTimerState { it.copy(status = TimerStatus.STOPPED, failureReason = null, errorMessage = null) }
                assertNull(store.data.first()[stringPreferencesKey("failure_reason")])
                assertNull(repo.getTimerState().failureReason)
            }
        }
    }

    @Test fun nonErrorLegacyRecordIgnoresResidualFailureCode() = runBlocking {
        val file = File(temp.newFolder(), "timer.preferences_pb")
        withStore(file) { store, repo ->
            store.edit {
                it[stringPreferencesKey("status")] = "PAUSED"
                it[stringPreferencesKey("failure_reason")] = TimerFailure.ALARM_SCHEDULING.storageCode
            }
            assertEquals(TimerStatus.PAUSED, repo.getTimerState().status)
            assertNull(repo.getTimerState().failureReason)
        }
    }
}
