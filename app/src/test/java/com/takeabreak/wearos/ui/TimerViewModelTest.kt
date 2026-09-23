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

    @Test fun exactAlarmBlockerPreventsStartAndIdentifiesSettings() = runTest(dispatcher) {
        capabilities = capabilities.copy(exactAlarmsAllowed = false)
        viewModel.startTimer().join()
        assertEquals(TimerStatus.STOPPED, repository.getTimerState().status)
        assertTrue(scheduler.scheduledStates.isEmpty())
        assertEquals(FeedbackType.ERROR, viewModel.feedback.value?.type)
        assertEquals(SettingTarget.EXACT_ALARM, viewModel.feedback.value?.settingTarget)
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
        testResult = ReminderSelfTestResult("任意错误描述", true)
        viewModel.testWorkReminder()
        assertEquals(FeedbackType.ERROR, viewModel.feedback.value?.type)
        assertEquals(listOf(TimerPhase.BREAK, TimerPhase.WORK), testedPhases)
        assertEquals(testResult.message, viewModel.feedback.value?.message)
        viewModel.clearFeedback()
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
