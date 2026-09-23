package com.takeabreak.wearos.timer

import com.takeabreak.wearos.alarm.AlarmScheduler
import com.takeabreak.wearos.notification.ReminderNotifier
import com.takeabreak.wearos.notification.ReminderTestResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

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

    override fun sendTestReminder(phase: TimerPhase) = ReminderTestResult("Test vibration requested", false)
}

class TimerEngineTest {

    private lateinit var clock: FakeClockProvider
    private lateinit var repo: FakeTimerRepository
    private lateinit var scheduler: FakeAlarmScheduler
    private lateinit var notifier: FakeReminderNotifier
    private lateinit var engine: TimerEngine

    @Before
    fun setup() {
        clock = FakeClockProvider()
        repo = FakeTimerRepository()
        scheduler = FakeAlarmScheduler()
        notifier = FakeReminderNotifier()
        engine = TimerEngine(repo, scheduler, notifier, clock, com.takeabreak.wearos.timer.FakeStopIntentStore())
    }

    @Test
    fun testStart_InitializesSessionAndSchedulesAlarm() = runBlocking {
        val result = engine.start(customWorkMin = 60, customBreakMin = 5)
        assertTrue(result.isSuccess)

        val state = repo.getTimerState()
        assertEquals(TimerStatus.RUNNING, state.status)
        assertEquals(TimerPhase.WORK, state.phase)
        assertEquals(1, state.currentRound)
        assertEquals(clock.elapsed + 60 * 60 * 1000L, state.deadlineElapsedRealtimeMs)
        assertEquals(1, scheduler.scheduledStates.size)
    }

    @Test
    fun testStart_IdempotentWhenAlreadyRunning() = runBlocking {
        engine.start(customWorkMin = 60, customBreakMin = 5)
        val firstSession = repo.getTimerState().sessionId

        // 第二次点击开始，不应产生新 session
        val secondResult = engine.start()
        assertTrue(secondResult.isSuccess)
        assertEquals(firstSession, repo.getTimerState().sessionId)
        assertEquals(1, scheduler.scheduledStates.size)
    }

    @Test
    fun testPauseAndResume_PreservesRemainingTime() = runBlocking {
        engine.start(customWorkMin = 60, customBreakMin = 5)
        val initialGeneration = repo.getTimerState().generation

        // 运行 10 分钟后暂停
        clock.advance(10 * 60 * 1000L)
        val pauseResult = engine.pause()
        assertTrue(pauseResult.isSuccess)

        val pausedState = repo.getTimerState()
        assertEquals(TimerStatus.PAUSED, pausedState.status)
        // 剩余约 50 分钟
        assertEquals(50 * 60 * 1000L, pausedState.pausedRemainingMs)
        assertTrue(scheduler.cancelledGenerations.contains(initialGeneration))

        // 暂停 15 分钟后继续
        clock.advance(15 * 60 * 1000L)
        val resumeResult = engine.resume()
        assertTrue(resumeResult.isSuccess)

        val resumedState = repo.getTimerState()
        assertEquals(TimerStatus.RUNNING, resumedState.status)
        assertEquals(60 * 60 * 1000L, resumedState.phaseTotalDurationMs)
        // 截止时间从当前时刻继续延后 50 分钟
        assertEquals(clock.elapsed + 50 * 60 * 1000L, resumedState.deadlineElapsedRealtimeMs)
    }

    @Test
    fun testStop_CancelsAlarmAndResetsSession() = runBlocking {
        engine.start(customWorkMin = 60, customBreakMin = 5)
        val initialGeneration = repo.getTimerState().generation

        val stopResult = engine.stop()
        assertTrue(stopResult.isSuccess)

        val state = repo.getTimerState()
        assertEquals(TimerStatus.STOPPED, state.status)
        assertEquals("", state.sessionId)
        assertEquals(1, state.currentRound)
        assertTrue(scheduler.cancelledGenerations.contains(initialGeneration))
    }

    @Test
    fun testPhaseTransition_WorkToBreak() = runBlocking {
        engine.start(customWorkMin = 60, customBreakMin = 5)
        val state = repo.getTimerState()

        // 推进到工作结束
        clock.advance(60 * 60 * 1000L)

        val transition = engine.onPhaseAlarm(
            sessionId = state.sessionId,
            generation = state.generation,
            expectedPhase = TimerPhase.WORK,
            round = 1
        )

        assertTrue(transition is PhaseTransitionResult.Success)
        val newState = (transition as PhaseTransitionResult.Success).newState
        assertEquals(TimerPhase.BREAK, newState.phase)
        assertEquals(1, newState.currentRound) // 休息仍为第 1 轮
        assertEquals(5 * 60 * 1000L, newState.phaseTotalDurationMs)
        assertEquals(1, notifier.phaseReminders.size)
        assertTrue(notifier.phaseReminders[0].startsWith("BREAK-1-5"))
    }

    @Test
    fun testPhaseTransition_BreakToNextWork_IncrementsRound() = runBlocking {
        engine.start(customWorkMin = 60, customBreakMin = 5)
        val s1 = repo.getTimerState()

        // 第 1 轮工作到期
        clock.advance(60 * 60 * 1000L)
        engine.onPhaseAlarm(s1.sessionId, s1.generation, TimerPhase.WORK, 1)

        val s2 = repo.getTimerState()
        assertEquals(TimerPhase.BREAK, s2.phase)

        // 第 1 轮休息到期
        clock.advance(5 * 60 * 1000L)
        val transition = engine.onPhaseAlarm(s2.sessionId, s2.generation, TimerPhase.BREAK, 1)

        assertTrue(transition is PhaseTransitionResult.Success)
        val s3 = (transition as PhaseTransitionResult.Success).newState
        assertEquals(TimerPhase.WORK, s3.phase)
        assertEquals(2, s3.currentRound) // 轮数增加至第 2 轮
    }

    @Test
    fun testPhaseAlarm_StaleGeneration_IsIgnored() = runBlocking {
        engine.start(customWorkMin = 60, customBreakMin = 5)
        val s1 = repo.getTimerState()

        // 用户暂停后再恢复，导致 generation 递增
        engine.pause()
        engine.resume()

        val staleTransition = engine.onPhaseAlarm(
            sessionId = s1.sessionId,
            generation = s1.generation, // 旧 generation
            expectedPhase = TimerPhase.WORK,
            round = 1
        )

        assertTrue(staleTransition is PhaseTransitionResult.Ignored)
    }

    @Test
    fun testScheduleFailure_TransitionsToErrorState() = runBlocking {
        scheduler.canSchedule = false // 模拟系统闹钟权限失效
        val result = engine.start(customWorkMin = 60, customBreakMin = 5)
        assertTrue(result.isFailure)

        val state = repo.getTimerState()
        assertEquals(TimerStatus.ERROR, state.status)
        assertEquals(1, notifier.errorNotifications.size)
        assertEquals(0, notifier.phaseReminders.size)
        assertEquals(60 * 60 * 1000L, state.pausedRemainingMs)
    }

    @Test
    fun testErrorRetry_AfterPermissionRestored_Succeeds() = runBlocking {
        scheduler.canSchedule = false
        val startResult = engine.start(customWorkMin = 60, customBreakMin = 5)
        assertTrue(startResult.isFailure)
        assertEquals(TimerStatus.ERROR, repo.getTimerState().status)

        // 恢复权限后调用 retry
        scheduler.canSchedule = true
        val retryResult = engine.retry()
        assertTrue(retryResult.isSuccess)

        val state = repo.getTimerState()
        assertEquals(TimerStatus.RUNNING, state.status)
        assertEquals(clock.elapsed + 60 * 60 * 1000L, state.deadlineElapsedRealtimeMs)
    }

    @Test
    fun testSystemEvent_TimeChanged_ReconcilesDeadline() = runBlocking {
        engine.start(customWorkMin = 60, customBreakMin = 5)
        val initialGen = repo.getTimerState().generation

        // 推进 20 分钟后发生系统改时
        clock.advance(20 * 60 * 1000L)
        val state = engine.onSystemEvent(TimerSystemEvent.TIME_CHANGED)

        assertEquals(TimerStatus.RUNNING, state.status)
        assertEquals(clock.elapsed + 40 * 60 * 1000L, state.deadlineElapsedRealtimeMs)
        // 旧 generation 闹钟已被明确取消
        assertTrue(scheduler.cancelledGenerations.contains(initialGen))
    }

    @Test
    fun testSystemEvent_BootCompleted_ReconcilesRemaining() = runBlocking {
        engine.start(customWorkMin = 60, customBreakMin = 5)
        val initialGen = repo.getTimerState().generation

        // 重启：wall clock 过了 15 分钟，elapsedRealtime 归零
        clock.wall += 15 * 60 * 1000L
        clock.elapsed = 5000L // 开机 5 秒

        val state = engine.onSystemEvent(TimerSystemEvent.BOOT_COMPLETED)
        assertEquals(TimerStatus.RUNNING, state.status)
        // 剩余 45 分钟 = 45 * 60 * 1000L
        assertEquals(clock.elapsed + 45 * 60 * 1000L, state.deadlineElapsedRealtimeMs)
        assertTrue(scheduler.cancelledGenerations.contains(initialGen))
    }

    @Test
    fun testSystemEvent_BootCompleted_Expired_PausesAndAwaitsConfirmation() = runBlocking {
        engine.start(customWorkMin = 60, customBreakMin = 5)

        // 重启：wall clock 过了 70 分钟，上阶段工作已在关机期间到期
        clock.wall += 70 * 60 * 1000L
        clock.elapsed = 10000L // 开机 10 秒

        // 真正重启后阶段已过期：遵循原设计，置为暂停并提示继续，不开机后擅自补发阶段振动
        val state = engine.onSystemEvent(TimerSystemEvent.BOOT_COMPLETED)
        assertEquals(TimerStatus.PAUSED, state.status)
        assertEquals(0L, state.pausedRemainingMs)
        assertEquals(0, notifier.phaseReminders.size) // 开机时不擅自补发阶段振动

        // 用户确认继续后，安全平滑过渡到下一阶段（第 1 轮休息 5 分钟）
        val resumeResult = engine.resume()
        assertTrue(resumeResult.isSuccess)
        val resumedState = repo.getTimerState()
        assertEquals(TimerStatus.RUNNING, resumedState.status)
        assertEquals(TimerPhase.BREAK, resumedState.phase)
        assertEquals(1, resumedState.currentRound)
        assertEquals(5 * 60 * 1000L, resumedState.phaseTotalDurationMs)
        assertEquals(clock.elapsed + 5 * 60 * 1000L, resumedState.deadlineElapsedRealtimeMs)
    }

    @Test
    fun testStop_CancelsAllAlarmsAndClearsAllNotifications() = runBlocking {
        engine.start(customWorkMin = 60, customBreakMin = 5)
        engine.stop()

        assertTrue(scheduler.allAlarmsCancelledCount > 0)
        assertEquals(0, notifier.phaseReminders.size)
        assertEquals(null, notifier.statusShown)
    }

    @Test
    fun testStop_CannotResumeWithoutExplicitStart() = runBlocking {
        engine.start(customWorkMin = 45, customBreakMin = 5)
        engine.stop()

        val stateAfterStop = repo.getTimerState()
        assertEquals(TimerStatus.STOPPED, stateAfterStop.status)

        // 用户已主动停止，调用 resume 必须安全拒绝，绝不以恢复为由偷偷重启计时
        val resumeResult = engine.resume()
        assertTrue(resumeResult.isFailure)
        assertEquals(TimerStatus.STOPPED, repo.getTimerState().status)
        assertEquals(0, scheduler.scheduledStates.size - 1) // 没有新的闹钟被排定
    }

    @Test
    fun testSystemEvent_TimeSet_DoesNotCorruptRunningPhaseWhenElapsedValid() = runBlocking {
        engine.start(customWorkMin = 45, customBreakMin = 5)
        val initialGen = repo.getTimerState().generation

        // 仅系统时间手动调整 (Wall Clock 倒拨 1 小时)，但开机时间 elapsedRealtime 保持单调递增
        clock.wall -= 3600 * 1000L
        clock.advance(5000L) // 运行了 5 秒

        val reconciledState = engine.onSystemEvent(TimerSystemEvent.TIME_CHANGED)
        // 状态依然是 RUNNING，deadline 基于单调 elapsedRealtime 保持准确定时
        assertEquals(TimerStatus.RUNNING, reconciledState.status)
        assertTrue(reconciledState.deadlineElapsedRealtimeMs > clock.elapsed)
    }

    @Test
    fun testStop_FailurePreservesStopIntent_BlocksReconcileAndStaleBroadcast() = runBlocking {
        engine.start(customWorkMin = 60, customBreakMin = 5)
        val runningState = engine.getTimerState()
        assertEquals(TimerStatus.RUNNING, runningState.status)

        // 模拟后续 stop 操作遭遇持续存储落盘失败
        repo.failWrites = true
        val stopResult = engine.stop()
        assertTrue(stopResult.isFailure) // 明确返回 failure 给调用方

        // 验证 1：闹钟已被全面撤销，同进程内存权威状态已置为 STOPPED
        assertTrue(scheduler.allAlarmsCancelledCount > 0)
        val stateAfterFailedStop = engine.getTimerState()
        assertEquals(TimerStatus.STOPPED, stateAfterFailedStop.status)

        // 验证 2：晚到的阶段到期广播，即便持有旧 sessionId，也因停止意图被安全忽略
        val alarmResult = engine.onPhaseAlarm(
            sessionId = runningState.sessionId,
            generation = runningState.generation,
            expectedPhase = TimerPhase.WORK,
            round = 1
        )
        assertTrue(alarmResult is PhaseTransitionResult.Ignored)

        // 验证 3：存储恢复正常后触发系统对账事件，绝不以系统恢复为由偷偷重新安排闹钟
        repo.failWrites = false
        val reconciled = engine.onSystemEvent(TimerSystemEvent.TIME_CHANGED)
        assertEquals(TimerStatus.STOPPED, reconciled.status)
        assertEquals(0L, reconciled.deadlineElapsedRealtimeMs)
    }

    @Test
    fun testHandleNotificationAction_DelegatesToEngine_ValidatesSession() = runBlocking {
        engine.start(customWorkMin = 45, customBreakMin = 5)
        val runningState = engine.getTimerState()

        // 1. 错误的 sessionId 动作被拦截
        val invalidSessionResult = engine.handleNotificationAction(
            action = com.takeabreak.wearos.notification.NotificationActions.PAUSE,
            expectedSessionId = "stale-session-id"
        )
        assertTrue(invalidSessionResult.isFailure)
        assertEquals(TimerStatus.RUNNING, engine.getTimerState().status)

        // 2. 正确的 sessionId 成功在锁内执行暂停
        val validPauseResult = engine.handleNotificationAction(
            action = com.takeabreak.wearos.notification.NotificationActions.PAUSE,
            expectedSessionId = runningState.sessionId
        )
        assertTrue(validPauseResult.isSuccess)
        assertEquals(TimerStatus.PAUSED, engine.getTimerState().status)
    }

    @Test
    fun testHandleNotificationAction_BlockedPreflight_DoesNotCorruptState() = runBlocking {
        engine.start(customWorkMin = 45, customBreakMin = 5)
        engine.pause()
        val pausedState = engine.getTimerState()
        assertEquals(TimerStatus.PAUSED, pausedState.status)

        // 模拟通知点击“继续”，但权限检查被阻止
        val blockedResult = engine.handleNotificationAction(
            action = com.takeabreak.wearos.notification.NotificationActions.RESUME,
            expectedSessionId = pausedState.sessionId,
            preflightChecker = {
                com.takeabreak.wearos.permission.PreflightCheckResult.Blocked(
                    reason = "精确闹钟权限缺失",
                    target = com.takeabreak.wearos.permission.SettingTarget.EXACT_ALARM
                )
            }
        )
        assertTrue(blockedResult.isFailure)
        // 保持原 PAUSED 状态，绝不伪造旧 ERROR 快照覆盖当前会话
        assertEquals(TimerStatus.PAUSED, engine.getTimerState().status)
        assertTrue(notifier.errorNotifications.any { it.contains("精确闹钟权限缺失") })
    }

    @Test
    fun testContinuousStorageFailure_TransitionsToFaultState() = runBlocking {
        engine.start(customWorkMin = 45, customBreakMin = 5)
        assertEquals(TimerStatus.RUNNING, engine.getTimerState().status)

        // 模拟连续存储失败
        repo.failWrites = true
        val pauseResult = engine.pause()
        assertTrue(pauseResult.isFailure)

        // 权威状态明确转为 ERROR 故障状态
        val faultState = engine.getTimerState()
        assertEquals(TimerStatus.ERROR, faultState.status)
        assertEquals("存储故障，计时已中断", faultState.errorMessage)
    }
}
