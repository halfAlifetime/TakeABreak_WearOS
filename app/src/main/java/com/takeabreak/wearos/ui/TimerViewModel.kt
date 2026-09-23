package com.takeabreak.wearos.ui

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.takeabreak.wearos.notification.ReminderSelfTest
import com.takeabreak.wearos.permission.ReminderCapabilityReader
import com.takeabreak.wearos.timer.ClockProvider
import com.takeabreak.wearos.permission.ReminderDiagnostics
import com.takeabreak.wearos.permission.ReminderMessages
import com.takeabreak.wearos.permission.ReminderPolicy
import com.takeabreak.wearos.permission.SettingTarget
import com.takeabreak.wearos.timer.TimerEngine
import com.takeabreak.wearos.timer.TimerPhase
import com.takeabreak.wearos.timer.TimerState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class NavigationCommand(val id: Long, val route: String)

class TimerViewModel(
    private val timerEngine: TimerEngine,
    private val clockProvider: ClockProvider,
    private val capabilityReader: ReminderCapabilityReader,
    private val reminderSelfTest: ReminderSelfTest
) : ViewModel() {

    // null means loading, never a synthetic STOPPED session. The UI exposes no timer
    // controls until the authoritative snapshot has arrived.
    val timerState: StateFlow<TimerState?> = timerEngine.timerStateFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    // 前台刷新 tick，仅供界面重绘倒计时与进度条，无业务副作用
    private val _uiTickElapsed = mutableLongStateOf(clockProvider.elapsedRealtime())
    val uiTickElapsed: State<Long> = _uiTickElapsed

    // 界面可见性与轻量级 ticker 控制（界面不可见时彻底挂起，杜绝后台无谓空转）
    private var isUiVisible = false
    private var tickerJob: Job? = null

    // Diagnostics and preflight are derived from the same system snapshot.
    private val _permissionState = mutableStateOf(ReminderPolicy.diagnose(capabilityReader.read()))
    val permissionState: State<ReminderDiagnostics> = _permissionState

    // 操作反馈模型（区分 ERROR / SUCCESS / INFO，避免成功提示弹错误红框）
    private val _feedback = mutableStateOf<ActionFeedback?>(null)
    val feedback: State<ActionFeedback?> = _feedback

    // 导航命令（用于表盘小图标/通知点击等单次导航意图）
    private var navCounter = 0L
    private val _navigationCommand = mutableStateOf<NavigationCommand?>(null)
    val navigationCommand: State<NavigationCommand?> = _navigationCommand

    init {
        _uiTickElapsed.longValue = clockProvider.elapsedRealtime()
    }

    fun requestNavigateToTimer() {
        _navigationCommand.value = NavigationCommand(++navCounter, "timer")
    }

    fun consumeNavigationCommand(id: Long) {
        if (_navigationCommand.value?.id == id) {
            _navigationCommand.value = null
        }
    }

    fun setUiVisible(visible: Boolean) {
        isUiVisible = visible
        if (visible) {
            _uiTickElapsed.longValue = clockProvider.elapsedRealtime()
            if (tickerJob == null || tickerJob?.isActive == false) {
                tickerJob = viewModelScope.launch {
                    while (isActive && isUiVisible) {
                        _uiTickElapsed.longValue = clockProvider.elapsedRealtime()
                        delay(500L)
                    }
                }
            }
        } else {
            tickerJob?.cancel()
            tickerJob = null
        }
    }

    fun refreshPermissions() {
        _permissionState.value = ReminderPolicy.diagnose(capabilityReader.read())
    }

    fun clearFeedback() {
        _feedback.value = null
    }

    fun showFeedback(message: String, type: FeedbackType = FeedbackType.INFO, target: SettingTarget = SettingTarget.NONE) {
        _feedback.value = ActionFeedback(message, type, target)
    }

    private fun performPreflightCheck(): Boolean {
        refreshPermissions()
        val decision = _permissionState.value.command
        _feedback.value = when {
            decision.blocker != null -> ActionFeedback(
                ReminderMessages.describe(decision.blocker), FeedbackType.ERROR, decision.blocker.target
            )
            decision.warnings.isNotEmpty() -> ActionFeedback(ReminderMessages.command(decision), FeedbackType.INFO)
            else -> null
        }
        return decision.allowed
    }

    fun startTimer(): Job = viewModelScope.launch {
        if (timerState.value == null) return@launch
        if (!performPreflightCheck()) return@launch
        val result = runCatching { timerEngine.start() }.getOrElse { Result.failure(it) }
        if (result.isFailure) {
            _feedback.value = ActionFeedback(
                message = result.exceptionOrNull()?.message ?: "启动失败",
                type = FeedbackType.ERROR,
                settingTarget = SettingTarget.NONE
            )
        }
    }

    fun pauseTimer(): Job = viewModelScope.launch {
        if (timerState.value == null) return@launch
        val result = runCatching { timerEngine.pause() }.getOrElse { Result.failure(it) }
        if (result.isFailure) {
            _feedback.value = ActionFeedback(
                message = result.exceptionOrNull()?.message ?: "暂停保存失败",
                type = FeedbackType.ERROR,
                settingTarget = SettingTarget.NONE
            )
        }
    }

    fun resumeTimer(): Job = viewModelScope.launch {
        if (timerState.value == null) return@launch
        if (!performPreflightCheck()) return@launch
        val result = runCatching { timerEngine.resume() }.getOrElse { Result.failure(it) }
        if (result.isFailure) {
            _feedback.value = ActionFeedback(
                message = result.exceptionOrNull()?.message ?: "恢复失败",
                type = FeedbackType.ERROR,
                settingTarget = SettingTarget.NONE
            )
        }
    }

    fun retryTimer(): Job = viewModelScope.launch {
        if (timerState.value == null) return@launch
        if (!performPreflightCheck()) return@launch
        val result = runCatching { timerEngine.retry() }.getOrElse { Result.failure(it) }
        if (result.isFailure) {
            _feedback.value = ActionFeedback(
                message = result.exceptionOrNull()?.message ?: "重试失败",
                type = FeedbackType.ERROR,
                settingTarget = SettingTarget.NONE
            )
        } else if (_feedback.value == null) {
            _feedback.value = ActionFeedback(
                message = "已重新恢复计时",
                type = FeedbackType.SUCCESS,
                settingTarget = SettingTarget.NONE
            )
        }
    }

    fun stopTimer(): Job = viewModelScope.launch {
        if (timerState.value == null) return@launch
        val result = runCatching { timerEngine.stop() }.getOrElse { Result.failure(it) }
        if (result.isFailure) {
            _feedback.value = ActionFeedback(
                message = result.exceptionOrNull()?.message ?: "停止保存失败",
                type = FeedbackType.ERROR,
                settingTarget = SettingTarget.NONE
            )
        } else {
            _feedback.value = null
        }
    }

    fun setWorkDuration(minutes: Int): Job = viewModelScope.launch {
        if (timerState.value == null) return@launch
        val res = runCatching {
            timerEngine.updateWorkDuration(minutes)
        }.getOrElse { Result.failure(it) }
        if (res.isFailure) {
            _feedback.value = ActionFeedback(
                message = res.exceptionOrNull()?.message ?: "修改时长失败",
                type = FeedbackType.ERROR,
                settingTarget = SettingTarget.NONE
            )
        }
    }

    fun setBreakDuration(minutes: Int): Job = viewModelScope.launch {
        if (timerState.value == null) return@launch
        val res = runCatching {
            timerEngine.updateBreakDuration(minutes)
        }.getOrElse { Result.failure(it) }
        if (res.isFailure) {
            _feedback.value = ActionFeedback(
                message = res.exceptionOrNull()?.message ?: "修改时长失败",
                type = FeedbackType.ERROR,
                settingTarget = SettingTarget.NONE
            )
        }
    }

    fun testBreakReminder() {
        testReminder(TimerPhase.BREAK)
    }

    fun testWorkReminder() {
        testReminder(TimerPhase.WORK)
    }

    private fun testReminder(phase: TimerPhase) {
        refreshPermissions()
        val result = reminderSelfTest.run(phase)
        _feedback.value = ActionFeedback(
            message = result.message,
            type = if (result.isError) FeedbackType.ERROR else FeedbackType.INFO,
            settingTarget = SettingTarget.NONE
        )
    }
}
