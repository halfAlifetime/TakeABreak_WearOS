package com.takeabreak.wearos.ui

import com.takeabreak.wearos.permission.support.readyCapabilities
import androidx.lifecycle.ViewModelStore
import com.takeabreak.wearos.notification.ReminderSelfTestResult
import com.takeabreak.wearos.permission.InterruptionMode
import com.takeabreak.wearos.permission.ReminderCapabilityReader
import com.takeabreak.wearos.permission.SettingTarget
import com.takeabreak.wearos.timer.support.FakeAlarmScheduler
import com.takeabreak.wearos.timer.support.FakeClockProvider
import com.takeabreak.wearos.timer.support.FakeReminderNotifier
import com.takeabreak.wearos.timer.support.FakeStopIntentStore
import com.takeabreak.wearos.timer.support.FakeTimerRepository
import com.takeabreak.wearos.timer.TimerEngine
import com.takeabreak.wearos.timer.TimerPhase
import com.takeabreak.wearos.timer.TimerStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TimerViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val clock = FakeClockProvider()
    private val repository = FakeTimerRepository()
    private val scheduler = FakeAlarmScheduler()
    private val engine = TimerEngine(repository, scheduler, FakeReminderNotifier(), clock, FakeStopIntentStore())
    private var capabilities = readyCapabilities()
    private var testResult = ReminderSelfTestResult("请求已提交，请确认触感", false)
    private val testedPhases = mutableListOf<TimerPhase>()
    private lateinit var viewModel: TimerViewModel
    private lateinit var stateCollection: Job

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        viewModel = TimerViewModel(engine, clock, ReminderCapabilityReader { capabilities }) { phase ->
            testedPhases += phase
            testResult
        }
        store.put("timer", viewModel)
        stateCollection = viewModel.timerState.launchIn(CoroutineScope(dispatcher))
        dispatcher.scheduler.runCurrent()
    }

    @After fun cleanup() {
        stateCollection.cancel()
        store.clear()
        dispatcher.scheduler.runCurrent()
        Dispatchers.resetMain()
    }

    @Test fun independentDurationCommandsPreserveBothValues() = runTest(dispatcher) {
        val work = viewModel.setWorkDuration(45)
        val rest = viewModel.setBreakDuration(10)
        work.join()
        rest.join()
        val saved = repository.getTimerState()
        assertEquals(45, saved.workDurationMinutes)
        assertEquals(10, saved.breakDurationMinutes)
        assertNull(viewModel.feedback.value)
    }

    @Test fun successfulWorkSaveClearsPreviousSaveError() = runTest(dispatcher) {
        repository.failWrites = true
        viewModel.setWorkDuration(45).join()
        assertEquals(FeedbackType.ERROR, viewModel.feedback.value?.type)
        assertEquals(FeedbackSource.WORK_DURATION, viewModel.feedback.value?.source)
        repository.failWrites = false
        viewModel.setWorkDuration(45).join()
        assertEquals(45, repository.getTimerState().workDurationMinutes)
        assertNull(viewModel.feedback.value)
    }

    @Test fun successfulBreakSaveClearsPreviousSaveError() = runTest(dispatcher) {
        repository.failWrites = true
        viewModel.setBreakDuration(10).join()
        assertEquals(FeedbackType.ERROR, viewModel.feedback.value?.type)
        assertEquals(FeedbackSource.BREAK_DURATION, viewModel.feedback.value?.source)
        repository.failWrites = false
        viewModel.setBreakDuration(10).join()
        assertEquals(10, repository.getTimerState().breakDurationMinutes)
        assertNull(viewModel.feedback.value)
    }

    @Test fun successfulWorkSaveKeepsBreakSaveError() = runTest(dispatcher) {
        repository.failWrites = true
        viewModel.setBreakDuration(10).join()
        val breakError = viewModel.feedback.value!!
        repository.failWrites = false
        viewModel.setWorkDuration(45).join()
        assertEquals(45, repository.getTimerState().workDurationMinutes)
        assertEquals(5, repository.getTimerState().breakDurationMinutes)
        assertEquals(breakError, viewModel.feedback.value)
    }

    @Test fun successfulBreakSaveKeepsWorkSaveError() = runTest(dispatcher) {
        repository.failWrites = true
        viewModel.setWorkDuration(45).join()
        val workError = viewModel.feedback.value!!
        repository.failWrites = false
        viewModel.setBreakDuration(10).join()
        assertEquals(10, repository.getTimerState().breakDurationMinutes)
        assertEquals(60, repository.getTimerState().workDurationMinutes)
        assertEquals(workError, viewModel.feedback.value)
    }

    @Test fun completingDurationSavePreservesNewerSelfTestFeedback() = runTest(dispatcher) {
        val writeStarted = CompletableDeferred<Unit>()
        val releaseWrite = CompletableDeferred<Unit>()
        repository.beforeUpdate = {
            writeStarted.complete(Unit)
            releaseWrite.await()
        }
        val save = viewModel.setWorkDuration(45)
        val selfTestFeedback = try {
            writeStarted.await()
            viewModel.testBreakReminder()
            viewModel.feedback.value!!
        } finally {
            releaseWrite.complete(Unit)
        }
        save.join()
        assertEquals(45, repository.getTimerState().workDurationMinutes)
        assertEquals(FeedbackSource.REMINDER_TEST, selfTestFeedback.source)
        assertEquals(selfTestFeedback, viewModel.feedback.value)
    }

    @Test fun dismissingPreviousFeedbackPreservesSettingsLaunchFailure() {
        viewModel.showFeedback("Open settings", target = SettingTarget.EXACT_ALARM)
        val previousId = viewModel.feedback.value!!.id
        val dismissPrevious = { viewModel.clearFeedback(previousId) }
        viewModel.showFeedback("Settings launch failed")
        val failure = viewModel.feedback.value
        dismissPrevious()
        assertEquals(failure, viewModel.feedback.value)
    }

    @Test fun dismissingOldFeedbackCannotClearRepeatedMessage() {
        viewModel.showFeedback("Repeatable failure")
        val previous = viewModel.feedback.value!!
        viewModel.showFeedback("Repeatable failure")
        val current = viewModel.feedback.value!!
        assertTrue(current.id > previous.id)
        viewModel.clearFeedback(previous.id)
        assertEquals(current, viewModel.feedback.value)
        viewModel.clearFeedback(current.id)
        assertNull(viewModel.feedback.value)
    }

    @Test fun exactAlarmBlockerPreventsStartAndIdentifiesSettings() = runTest(dispatcher) {
        capabilities = capabilities.copy(exactAlarmsAllowed = false)
        viewModel.startTimer().join()
        assertEquals(TimerStatus.STOPPED, repository.getTimerState().status)
        assertTrue(scheduler.scheduledStates.isEmpty())
        assertEquals(FeedbackType.ERROR, viewModel.feedback.value?.type)
        assertEquals(SettingTarget.EXACT_ALARM, viewModel.feedback.value?.settingTarget)
        assertEquals(FeedbackSource.TIMER, viewModel.feedback.value?.source)
    }

    @Test fun dndWarningAllowsTimerAndSurvivesSuccessfulStart() = runTest(dispatcher) {
        capabilities = capabilities.copy(interruptionMode = InterruptionMode.PRIORITY)
        viewModel.startTimer().join()
        assertEquals(TimerStatus.RUNNING, repository.getTimerState().status)
        assertEquals(FeedbackType.INFO, viewModel.feedback.value?.type)
        assertFalse(viewModel.permissionState.value.workVibration.allowed)
        assertTrue(viewModel.permissionState.value.command.allowed)
    }

    @Test fun selfTestUsesResultTypeRatherThanChineseKeywords() {
        // A successful request still asks for physical confirmation, so its type is INFO.
        testResult = ReminderSelfTestResult("提示中含失败字样也不推断类型", false)
        viewModel.testBreakReminder()
        assertEquals(FeedbackType.INFO, viewModel.feedback.value?.type)
        assertEquals(FeedbackSource.REMINDER_TEST, viewModel.feedback.value?.source)
        testResult = ReminderSelfTestResult("任意错误描述", true)
        viewModel.testWorkReminder()
        assertEquals(FeedbackType.ERROR, viewModel.feedback.value?.type)
        assertEquals(FeedbackSource.REMINDER_TEST, viewModel.feedback.value?.source)
        assertEquals(listOf(TimerPhase.BREAK, TimerPhase.WORK), testedPhases)
        assertEquals(testResult.message, viewModel.feedback.value?.message)
        viewModel.clearFeedback(viewModel.feedback.value!!.id)
        assertNull(viewModel.feedback.value)
    }

    @Test fun consumingOldNavigationCannotEraseNewCommand() {
        viewModel.requestNavigateToTimer()
        val first = viewModel.navigationCommand.value!!
        viewModel.requestNavigateToTimer()
        val second = viewModel.navigationCommand.value!!
        assertTrue(second.id > first.id)
        assertEquals("timer", second.route)
        viewModel.consumeNavigationCommand(first.id)
        assertEquals(second, viewModel.navigationCommand.value)
        viewModel.consumeNavigationCommand(second.id)
        viewModel.consumeNavigationCommand(second.id)
        assertNull(viewModel.navigationCommand.value)
        assertTrue(scheduler.scheduledStates.isEmpty())
    }

    @Test fun invisibleScreenStopsTickerAndReopeningRefreshesImmediately() = runTest(dispatcher) {
        viewModel.setUiVisible(true)
        runCurrent()
        clock.advance(500)
        advanceTimeBy(500)
        runCurrent()
        assertEquals(clock.elapsed, viewModel.uiTickElapsed.value)
        viewModel.setUiVisible(false)
        val hiddenAt = viewModel.uiTickElapsed.value
        clock.advance(1000)
        advanceTimeBy(1000)
        runCurrent()
        assertEquals(hiddenAt, viewModel.uiTickElapsed.value)
        viewModel.setUiVisible(true)
        assertEquals(clock.elapsed, viewModel.uiTickElapsed.value)
        viewModel.setUiVisible(false)
    }

    @Test fun refreshingPermissionsReplacesSnapshotWithoutTimerSideEffects() {
        capabilities = capabilities.copy(notificationsEnabled = false)
        viewModel.refreshPermissions()
        assertFalse(viewModel.permissionState.value.command.allowed)
        capabilities = readyCapabilities()
        viewModel.refreshPermissions()
        assertTrue(viewModel.permissionState.value.command.allowed)
        assertTrue(scheduler.scheduledStates.isEmpty())
    }
}
