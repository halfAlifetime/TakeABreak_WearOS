package com.takeabreak.wearos.ui

import android.app.Application
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.takeabreak.wearos.TakeABreakApplication
import com.takeabreak.wearos.permission.ReminderDiagnostics
import com.takeabreak.wearos.permission.ReminderMessages
import com.takeabreak.wearos.permission.ReminderPolicy
import com.takeabreak.wearos.permission.SettingTarget
import com.takeabreak.wearos.timer.TimerEngine
import com.takeabreak.wearos.timer.TimerPhase
import com.takeabreak.wearos.timer.TimerState
import com.takeabreak.wearos.timer.TimerStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class NavigationCommand(val id: Long, val route: String)

class TimerViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as TakeABreakApplication
    private val timerEngine: TimerEngine = app.timerEngine
    private val clockProvider = app.clockProvider

    val timerState: StateFlow<TimerState> = timerEngine.timerStateFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TimerState()
    )

    // 前台刷新 tick，仅供界面重绘倒计时与进度条，无业务副作用
    private val _uiTickElapsed = mutableLongStateOf(clockProvider.elapsedRealtime())
    val uiTickElapsed: State<Long> = _uiTickElapsed

    // 界面可见性与轻量级 ticker 控制（界面不可见时彻底挂起，杜绝后台无谓空转）
    private var isUiVisible = false
    private var tickerJob: Job? = null

    // Diagnostics and preflight are derived from the same system snapshot.
    private val _permissionState = mutableStateOf(ReminderPolicy.diagnose(app.capabilityReader.read()))
    val permissionState: State<ReminderDiagnostics> = _permissionState

    // 操作反馈模型（区分 ERROR / SUCCESS / INFO，避免成功提示弹错误红框）
    private val _feedback = mutableStateOf<ActionFeedback?>(null)
    val feedback: State<ActionFeedback?> = _feedback

    // 向后兼容属性
    val actionMessage: State<String?> = derivedStateOf { _feedback.value?.message }
    val actionSettingTarget: State<SettingTarget> = derivedStateOf {
        _feedback.value?.settingTarget ?: SettingTarget.NONE
    }

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
        _permissionState.value = ReminderPolicy.diagnose(app.capabilityReader.read())
    }

    fun clearActionMessage() {
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

    fun startTimer() {
        viewModelScope.launch {
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
    }

    fun pauseTimer() {
        viewModelScope.launch {
            val result = runCatching { timerEngine.pause() }.getOrElse { Result.failure(it) }
            if (result.isFailure) {
                _feedback.value = ActionFeedback(
                    message = result.exceptionOrNull()?.message ?: "暂停保存失败",
                    type = FeedbackType.ERROR,
                    settingTarget = SettingTarget.NONE
                )
            }
        }
    }

    fun resumeTimer() {
        viewModelScope.launch {
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
    }

    fun retryTimer() {
        viewModelScope.launch {
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
    }

    fun stopTimer() {
        viewModelScope.launch {
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
    }

    fun setWorkDuration(minutes: Int) {
        viewModelScope.launch {
            val state = timerState.value
            if (state.status != TimerStatus.STOPPED) {
                _feedback.value = ActionFeedback(
                    message = "计时运行或暂停中无法修改，请先停止",
                    type = FeedbackType.ERROR,
                    settingTarget = SettingTarget.NONE
                )
                return@launch
            }
            val res = runCatching {
                timerEngine.updateDurations(workMinutes = minutes, breakMinutes = state.breakDurationMinutes)
            }.getOrElse { Result.failure(it) }
            if (res.isFailure) {
                _feedback.value = ActionFeedback(
                    message = res.exceptionOrNull()?.message ?: "修改时长失败",
                    type = FeedbackType.ERROR,
                    settingTarget = SettingTarget.NONE
                )
            }
        }
    }

    fun setBreakDuration(minutes: Int) {
        viewModelScope.launch {
            val state = timerState.value
            if (state.status != TimerStatus.STOPPED) {
                _feedback.value = ActionFeedback(
                    message = "计时运行或暂停中无法修改，请先停止",
                    type = FeedbackType.ERROR,
                    settingTarget = SettingTarget.NONE
                )
                return@launch
            }
            val res = runCatching {
                timerEngine.updateDurations(workMinutes = state.workDurationMinutes, breakMinutes = minutes)
            }.getOrElse { Result.failure(it) }
            if (res.isFailure) {
                _feedback.value = ActionFeedback(
                    message = res.exceptionOrNull()?.message ?: "修改时长失败",
                    type = FeedbackType.ERROR,
                    settingTarget = SettingTarget.NONE
                )
            }
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
        val result = app.reminderNotifier.sendTestReminder(phase)
        _feedback.value = ActionFeedback(
            message = result.message,
            type = if (result.isError) FeedbackType.ERROR else FeedbackType.INFO,
            settingTarget = SettingTarget.NONE
        )
    }
}
