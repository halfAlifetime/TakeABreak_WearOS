package com.takeabreak.wearos.timer

import com.takeabreak.wearos.alarm.AlarmScheduler
import com.takeabreak.wearos.notification.ReminderNotifier
import com.takeabreak.wearos.notification.NotificationActions
import com.takeabreak.wearos.permission.PreflightCheckResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.util.Collections
import java.util.UUID

class TimerEngine(
    private val repository: TimerRepository,
    private val scheduler: AlarmScheduler,
    private val notifier: ReminderNotifier,
    private val clockProvider: ClockProvider,
    private val stopIntentStore: StopIntentStore
) {
    private val mutex = Mutex()

    // 内存中的权威有效状态：当持久化层发生异常或延迟时，该状态作为同进程内的绝对基准
    @Volatile private var inMemoryEffectiveState: TimerState? = null
    private val _effectiveStateFlow = MutableStateFlow<TimerState?>(null)
    // An unreadable snapshot is unknown, not proof that the timer/session has stopped.
    private var stateReadFailed = false

    // 记录明确被用户主动停止的会话 ID 集合与全局停止标志
    // 杜绝因历史存储失败留下的旧 RUNNING 状态被晚到广播或系统对账重新启动
    private val explicitlyStoppedSessions = Collections.synchronizedSet(mutableSetOf<String>())
    @Volatile private var isExplicitlyStopped: Boolean = false

    /**
     * 统一更新同进程权威内存状态并广播给 UI 与通知
     */
    private fun updateEffectiveState(state: TimerState) {
        stateReadFailed = false
        inMemoryEffectiveState = state
        _effectiveStateFlow.value = state
    }

    /**
     * 获取当前有效状态（优先使用权威内存状态，若无则读持久化仓库）
     */
    private suspend fun getEffectiveStateInternal(): TimerState {
        val mem = inMemoryEffectiveState
        if (mem != null && !stateReadFailed) return mem
        val fromRepo = try {
            repository.getTimerState()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            val unknown = TimerState(
                generation = stopIntentStore.stoppedThroughGeneration().coerceAtLeast(0L),
                status = TimerStatus.ERROR,
                failureReason = TimerFailure.STORAGE_READ,
                errorMessage = "暂时无法读取计时状态，请重试",
                lastEventResult = "计时状态读取失败"
            )
            updateEffectiveState(unknown)
            stateReadFailed = true
            return unknown
        }
        val effective = applyStopIntent(fromRepo)
        updateEffectiveState(effective)
        return effective
    }

    private fun applyStopIntent(fromRepo: TimerState): TimerState {
        // The stop journal survives process death independently of the main DataStore.
        val stoppedThrough = stopIntentStore.stoppedThroughGeneration()
        return if (fromRepo.generation <= stoppedThrough ||
            fromRepo.sessionId in explicitlyStoppedSessions ||
            stopIntentStore.isRecoveryBlocked(fromRepo.sessionId)
        ) {
            isExplicitlyStopped = true
            fromRepo.copy(
                sessionId = "",
                generation = maxOf(fromRepo.generation, stoppedThrough),
                status = TimerStatus.STOPPED,
                phase = TimerPhase.WORK,
                currentRound = 1,
                deadlineElapsedRealtimeMs = 0L,
                deadlineWallClockMs = 0L,
                pausedRemainingMs = 0L,
                failureReason = null,
                errorMessage = null,
                lastEventResult = "已主动停止"
            )
        } else fromRepo
    }

    /**
     * 权威状态流：保证 UI 与协调器看到的是完全一致的有效状态
     */
    val timerStateFlow: Flow<TimerState> = flow {
        getTimerState()
        emitAll(_effectiveStateFlow.filterNotNull())
    }

    suspend fun getTimerState(): TimerState = mutex.withLock {
        getEffectiveStateInternal()
    }

    /**
     * Waiting for the lock and the initial read remain cancellable. Once alarm changes
     * begin, finish the storage commit/compensation and publish one coherent state even
     * if the UI's coroutine is cancelled. Do not release the lock before that completes.
     */
    private suspend fun <T> mutate(block: suspend () -> T): T = mutex.withLock {
        getEffectiveStateInternal()
        withContext(NonCancellable + Dispatchers.IO) { block() }
    }

    /**
     * 有界重试持久化状态更新，保留协程 CancellationException 语义
     */
    private suspend fun safeUpdateStateWithRetry(
        maxAttempts: Int = 2,
        transform: (TimerState) -> TimerState
    ): Result<TimerState> {
        var lastException: Throwable? = null
        for (attempt in 1..maxAttempts) {
            try {
                val saved = repository.updateTimerState(transform)
                return Result.success(saved)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                lastException = e
                if (attempt < maxAttempts) {
                    delay(30L)
                }
            }
        }
        return Result.failure(lastException ?: IOException("存储更新失败"))
    }

    /**
     * 启动循环计时
     */
    suspend fun start(
        customWorkMin: Int? = null,
        customBreakMin: Int? = null
    ): Result<TimerState> = mutate {
        val current = getEffectiveStateInternal()
        // 幂等：若已在运行，直接返回当前状态，禁止产生多套循环
        if (current.status == TimerStatus.RUNNING) {
            return@mutate Result.success(current)
        }

        val workMinutes = customWorkMin ?: current.workDurationMinutes
        val breakMinutes = customBreakMin ?: current.breakDurationMinutes
        if (workMinutes <= 0 || breakMinutes <= 0) {
            return@mutate Result.failure(IllegalArgumentException("计时时长必须大于 0"))
        }

        val newSessionId = UUID.randomUUID().toString()
        val newGeneration = current.generation + 1L

        // Only explicit start can replace the stop gate, and only for this new UUID.
        // Persist it before side effects; a failed start must never reopen the old session.
        val allowStart = runCatching {
            if (stopIntentStore.isRecoveryBlocked(newSessionId)) {
                stopIntentStore.allowStartedSession(newSessionId)
            }
        }
        if (allowStart.isFailure) return@mutate Result.failure(allowStart.exceptionOrNull()!!)

        isExplicitlyStopped = false

        // 启动前彻底清理既往闹钟与残留通知
        scheduler.cancelPhaseAlarm(current.generation)
        scheduler.cancelAllPhaseAlarms()

        val workDurationMs = workMinutes * 60_000L
        val nowElapsed = clockProvider.elapsedRealtime()
        val nowWall = clockProvider.currentTimeMillis()

        val startState = TimerState(
            sessionId = newSessionId,
            generation = newGeneration,
            status = TimerStatus.RUNNING,
            phase = TimerPhase.WORK,
            currentRound = 1,
            workDurationMinutes = workMinutes,
            breakDurationMinutes = breakMinutes,
            phaseTotalDurationMs = workDurationMs,
            deadlineElapsedRealtimeMs = nowElapsed + workDurationMs,
            deadlineWallClockMs = nowWall + workDurationMs,
            pausedRemainingMs = 0L,
            bootIdentifier = nowElapsed,
            bootCount = clockProvider.bootCount(),
            lastEventId = "START_${newSessionId.take(8)}",
            lastEventResult = "开始第 1 轮工作",
            lastEventTimestampMs = nowWall,
            failureReason = null,
            errorMessage = null
        )

        // 尝试调度系统阶段闹钟
        val scheduled = scheduler.schedulePhaseAlarm(startState, previousGeneration = current.generation)
        if (!scheduled) {
            val errorState = startState.copy(
                status = TimerStatus.ERROR,
                failureReason = TimerFailure.ALARM_SCHEDULING,
                pausedRemainingMs = workDurationMs,
                deadlineElapsedRealtimeMs = 0L,
                deadlineWallClockMs = 0L,
                errorMessage = "无法调度精确闹钟，请检查手表设置中的精确闹钟与通知权限",
                lastEventResult = "精确闹钟调度失败"
            )
            scheduler.cancelPhaseAlarm(newGeneration)
            updateEffectiveState(errorState)
            runCatching { repository.updateTimerState { errorState } }
            notifier.showErrorNotification("精确闹钟调度失败，请检查系统设置", newSessionId)
            notifier.showStatusNotification(errorState)
            return@mutate Result.failure(IllegalStateException(errorState.errorMessage))
        }

        // 保存并发出前台状态通知（有界重试）
        val saveResult = safeUpdateStateWithRetry(maxAttempts = 2) { startState }
        if (saveResult.isSuccess) {
            val saved = saveResult.getOrThrow()
            updateEffectiveState(saved)
            notifier.clearAllNotifications()
            notifier.showStatusNotification(saved)
            return@mutate Result.success(saved)
        }

        // 写入连续失败：撤销已排定闹钟，发布明确故障状态，返回 Result.failure 而不是抛出让 UI 崩溃
        scheduler.cancelPhaseAlarm(newGeneration)
        val faultState = startState.copy(
            status = TimerStatus.ERROR,
            failureReason = TimerFailure.STORAGE_WRITE,
            pausedRemainingMs = workDurationMs,
            deadlineElapsedRealtimeMs = 0L,
            deadlineWallClockMs = 0L,
            errorMessage = "存储故障，启动失败",
            lastEventResult = "存储异常，启动已终止"
        )
        updateEffectiveState(faultState)
        runCatching { repository.updateTimerState { faultState } }
        notifier.showErrorNotification("存储故障，启动失败", newSessionId)
        notifier.showStatusNotification(faultState)
        return@mutate Result.failure(saveResult.exceptionOrNull() ?: IOException("存储更新失败"))
    }

    /**
     * 暂停当前计时
     */
    suspend fun pause(): Result<TimerState> = mutate {
        val current = getEffectiveStateInternal()
        if (stateReadFailed) return@mutate Result.failure(IOException("暂时无法读取计时状态，请重试"))
        pauseLocked(current)
    }

    private suspend fun pauseLocked(current: TimerState): Result<TimerState> {
        if (current.status != TimerStatus.RUNNING) {
            return Result.success(current)
        }

        val nowElapsed = clockProvider.elapsedRealtime()
        val nowWall = clockProvider.currentTimeMillis()
        val remainingMs = (current.deadlineElapsedRealtimeMs - nowElapsed).coerceAtLeast(0L)

        // 先取消当前已注册的闹钟，并增加 generation 废弃在途旧广播
        scheduler.cancelPhaseAlarm(current.generation)
        val nextGeneration = current.generation + 1L

        val pausedState = current.copy(
            generation = nextGeneration,
            status = TimerStatus.PAUSED,
            pausedRemainingMs = remainingMs,
            deadlineElapsedRealtimeMs = 0L,
            deadlineWallClockMs = 0L,
            lastEventResult = "已暂停，剩余 ${remainingMs / 1000}秒",
            lastEventTimestampMs = nowWall,
            failureReason = null,
            errorMessage = null
        )

        val saveResult = safeUpdateStateWithRetry(maxAttempts = 2) { pausedState }
        if (saveResult.isSuccess) {
            val saved = saveResult.getOrThrow()
            updateEffectiveState(saved)
            notifier.showStatusNotification(saved)
            return Result.success(saved)
        }

        // 持续落盘失败防御：旧闹钟已被取消，发布明确故障状态并同步内存权威状态
        val errorState = pausedState.copy(
            status = TimerStatus.ERROR,
            failureReason = TimerFailure.STORAGE_WRITE,
            errorMessage = "存储故障，计时已中断",
            lastEventResult = "暂停保存失败，转为错误态"
        )
        updateEffectiveState(errorState)
        runCatching { repository.updateTimerState { errorState } }
        notifier.showStatusNotification(errorState)
        return Result.failure(saveResult.exceptionOrNull() ?: IOException("暂停保存失败"))
    }

    /**
     * 继续运行已暂停或错误的计时
     */
    suspend fun resume(): Result<TimerState> = mutate {
        val current = getEffectiveStateInternal()
        resumeLocked(current)
    }

    private suspend fun resumeLocked(current: TimerState): Result<TimerState> {
        if (stateReadFailed) return Result.failure(IOException("暂时无法读取计时状态，请重试"))
        // STOPPED 状态不能直接 resume，必须通过 start 开启新计时
        if (current.status == TimerStatus.STOPPED) {
            return Result.failure(IllegalStateException("当前计时已停止，无法继续，请重新开始"))
        }

        // 若已经在运行，幂等成功
        if (current.status == TimerStatus.RUNNING) {
            return Result.success(current)
        }

        val target = TimerTransitions.resume(current)

        if (target.durationMs <= 0L) {
            return Result.failure(IllegalStateException("剩余时长无效，无法恢复"))
        }

        val nowElapsed = clockProvider.elapsedRealtime()
        val nowWall = clockProvider.currentTimeMillis()
        val nextGeneration = current.generation + 1L

        val resumedState = current.copy(
            generation = nextGeneration,
            status = TimerStatus.RUNNING,
            phase = target.phase,
            currentRound = target.round,
            phaseTotalDurationMs = target.totalDurationMs,
            pausedRemainingMs = 0L,
            deadlineElapsedRealtimeMs = nowElapsed + target.durationMs,
            deadlineWallClockMs = nowWall + target.durationMs,
            bootIdentifier = nowElapsed,
            bootCount = clockProvider.bootCount(),
            lastEventResult = if (target.advancesPhase) "前阶段已到期，确认继续进入 ${target.phase} (第 ${target.round} 轮)" else "已恢复计时",
            lastEventTimestampMs = nowWall,
            failureReason = null,
            errorMessage = null
        )

        // 显式清理旧 generation 闹钟，确保系统闹钟池只有唯一有效一条
        scheduler.cancelPhaseAlarm(current.generation)
        val scheduled = scheduler.schedulePhaseAlarm(resumedState, previousGeneration = current.generation)
        if (!scheduled) {
            scheduler.cancelPhaseAlarm(nextGeneration)
            val errorState = resumedState.copy(
                status = TimerStatus.ERROR,
                failureReason = TimerFailure.ALARM_SCHEDULING,
                pausedRemainingMs = target.durationMs,
                deadlineElapsedRealtimeMs = 0L,
                deadlineWallClockMs = 0L,
                errorMessage = "恢复失败：无法注册系统闹钟，请检查权限",
                lastEventResult = "恢复注册闹钟失败"
            )
            updateEffectiveState(errorState)
            runCatching { repository.updateTimerState { errorState } }
            notifier.showErrorNotification("恢复失败：无法注册系统闹钟", current.sessionId)
            notifier.showStatusNotification(errorState)
            return Result.failure(IllegalStateException(errorState.errorMessage))
        }

        val saveResult = safeUpdateStateWithRetry(maxAttempts = 2) { resumedState }
        if (saveResult.isSuccess) {
            val saved = saveResult.getOrThrow()
            updateEffectiveState(saved)
            notifier.clearReminderNotifications()
            notifier.showStatusNotification(saved)
            return Result.success(saved)
        }

        // 写入连续失败：撤回闹钟，发布明确故障状态并返回 Result.failure
        scheduler.cancelPhaseAlarm(nextGeneration)
        val faultState = resumedState.copy(
            status = TimerStatus.ERROR,
            failureReason = TimerFailure.STORAGE_WRITE,
            pausedRemainingMs = target.durationMs,
            deadlineElapsedRealtimeMs = 0L,
            deadlineWallClockMs = 0L,
            errorMessage = "存储故障，恢复失败",
            lastEventResult = "存储异常，恢复已终止"
        )
        updateEffectiveState(faultState)
        runCatching { repository.updateTimerState { faultState } }
        notifier.showErrorNotification("存储故障，恢复失败", current.sessionId)
        notifier.showStatusNotification(faultState)
        return Result.failure(saveResult.exceptionOrNull() ?: IOException("存储更新失败"))
    }

    /**
     * 错误状态重试入口
     */
    suspend fun retry(): Result<TimerState> = resume()

    /**
     * 停止计时并重置轮数，彻底清理旧闹钟与旧通知，保留时长偏好
     */
    suspend fun stop(): Result<TimerState> = mutate {
        val current = getEffectiveStateInternal()
        stopLocked(current)
    }

    private suspend fun stopLocked(current: TimerState): Result<TimerState> {
        val journalResult = runCatching {
            stopIntentStore.markStopped(current.generation)
        }
        // 彻底取消系统闹钟
        scheduler.cancelPhaseAlarm(current.generation)
        scheduler.cancelAllPhaseAlarms()

        isExplicitlyStopped = true
        if (current.sessionId.isNotEmpty()) {
            explicitlyStoppedSessions.add(current.sessionId)
        }

        val stoppedState = buildStoppedState(current)

        notifier.clearAllNotifications()
        updateEffectiveState(stoppedState)

        val saveResult = safeUpdateStateWithRetry(maxAttempts = 2) { stoppedState }
        if (saveResult.isSuccess) {
            val saved = saveResult.getOrThrow()
            updateEffectiveState(saved)
            return Result.success(saved)
        }

        // 用户明确点击停止后，绝不能以补偿或恢复为由偷偷重启旧计时器
        // 内存权威状态已经置为 STOPPED，闹钟已全部撤销，即使落盘失败也不得恢复任何闹钟
        val fallbackSave = runCatching { repository.updateTimerState { stoppedState } }
        if (fallbackSave.isSuccess) return Result.success(stoppedState)
        return Result.failure(IOException(
            if (journalResult.isSuccess) "计时已停止，状态保存失败，停止意图已保留"
            else "计时已在本次运行中停止，但停止记录保存失败",
            saveResult.exceptionOrNull()
        ))
    }

    private fun buildStoppedState(current: TimerState): TimerState = current.copy(
        sessionId = "",
        generation = current.generation + 1L,
        status = TimerStatus.STOPPED,
        phase = TimerPhase.WORK,
        currentRound = 1,
        deadlineElapsedRealtimeMs = 0L,
        deadlineWallClockMs = 0L,
        pausedRemainingMs = 0L,
        lastEventResult = "已主动停止",
        lastEventTimestampMs = clockProvider.currentTimeMillis(),
        failureReason = null,
        errorMessage = null
    )

    /** Persist an exact-session stop even when the current session cannot be read. */
    private suspend fun stopUnreadableSessionLocked(sessionId: String, unknown: TimerState): Result<TimerState> {
        val journalResult = runCatching { stopIntentStore.markSessionStopped(sessionId) }
        explicitlyStoppedSessions.add(sessionId)
        scheduler.cancelSessionAlarms(sessionId)
        notifier.clearSessionNotifications(sessionId)

        // edit may succeed even if the earlier read failed. Compare inside the atomic
        // update so an old notification cannot overwrite a newer persisted session.
        var matchedSession = false
        val saveResult = safeUpdateStateWithRetry { persisted ->
            matchedSession = persisted.sessionId == sessionId
            if (matchedSession) buildStoppedState(persisted) else persisted
        }
        if (saveResult.isSuccess) {
            val effective = applyStopIntent(saveResult.getOrThrow())
            updateEffectiveState(effective)
            if (matchedSession) {
                isExplicitlyStopped = true
                scheduler.cancelAllPhaseAlarms()
                notifier.clearAllNotifications()
                return Result.success(effective)
            }
            return Result.failure(IllegalStateException("通知所属会话已失效，操作已忽略"))
        }

        val message = if (journalResult.isSuccess) {
            "该通知的停止请求已保存，计时状态暂不可读取"
        } else {
            "通知停止请求保存失败，请重试"
        }
        updateEffectiveState(unknown.copy(errorMessage = message, failureReason = if (journalResult.isSuccess) TimerFailure.STORAGE_READ else TimerFailure.STORAGE_WRITE))
        stateReadFailed = true
        return Result.failure(IOException(message, saveResult.exceptionOrNull()))
    }

    /**
     * 统一处理通知栏动作广播（在同一互斥锁内完成会话校验、权限检查与状态操作，杜绝绕过锁直接改写仓库）
     */
    suspend fun handleNotificationAction(
        action: String,
        expectedSessionId: String?,
        preflightChecker: (() -> PreflightCheckResult)? = null
    ): Result<TimerState> = mutate {
        val current = getEffectiveStateInternal()

        // 1. 严格校验会话：防止旧通知或并发修改误触发
        if (expectedSessionId.isNullOrEmpty()) {
            return@mutate Result.failure(IllegalStateException("通知所属会话已失效，操作已忽略"))
        }

        if (stateReadFailed) {
            return@mutate if (action == NotificationActions.STOP) {
                stopUnreadableSessionLocked(expectedSessionId, current)
            } else {
                Result.failure(IOException("暂时无法读取计时状态，请重试"))
            }
        }
        if (expectedSessionId != current.sessionId) {
            return@mutate Result.failure(IllegalStateException("通知所属会话已失效，操作已忽略"))
        }

        when (action) {
            NotificationActions.PAUSE -> pauseLocked(current)
            NotificationActions.STOP -> stopLocked(current)
            NotificationActions.RESUME -> {
                // 统一执行前置能力检查
                if (preflightChecker != null) {
                    val check = preflightChecker()
                    if (check is PreflightCheckResult.Blocked) {
                        // 保持原 PAUSED 状态并记录原因，绝不强制伪造旧 ERROR 快照写回仓库破坏新会话
                        notifier.showErrorNotification("恢复被阻止：${check.reason}", current.sessionId)
                        return@mutate Result.failure(IllegalStateException(check.reason))
                    }
                }
                resumeLocked(current)
            }
            else -> Result.failure(IllegalArgumentException("未知通知动作: $action"))
        }
    }

    /**
     * 修改时长设置（仅允许在 STOPPED 状态修改）
     */
    suspend fun updateWorkDuration(minutes: Int): Result<Unit> = updateDurationFields(workMinutes = minutes)

    suspend fun updateBreakDuration(minutes: Int): Result<Unit> = updateDurationFields(breakMinutes = minutes)

    internal suspend fun updateDurations(workMinutes: Int, breakMinutes: Int): Result<Unit> =
        updateDurationFields(workMinutes, breakMinutes)

    // Merge only after obtaining the engine lock; callers never supply an unchanged stale field.
    private suspend fun updateDurationFields(workMinutes: Int? = null, breakMinutes: Int? = null): Result<Unit> = mutate {
        val current = getEffectiveStateInternal()
        if (current.status != TimerStatus.STOPPED) {
            return@mutate Result.failure(IllegalStateException("计时运行或暂停中无法修改时长，请先停止"))
        }

        val nextWork = workMinutes ?: current.workDurationMinutes
        val nextBreak = breakMinutes ?: current.breakDurationMinutes
        if (nextWork <= 0 || nextBreak <= 0) {
            return@mutate Result.failure(IllegalArgumentException("计时时长必须大于 0"))
        }
        val updatedState = current.copy(
            workDurationMinutes = nextWork,
            breakDurationMinutes = nextBreak,
            phaseTotalDurationMs = nextWork * 60_000L
        )
        safeUpdateStateWithRetry { updatedState }.map { saved ->
            updateEffectiveState(saved)
        }
    }

    /**
     * 系统闹钟到达时的权威状态转移入口
     */
    suspend fun onPhaseAlarm(
        sessionId: String,
        generation: Long,
        expectedPhase: TimerPhase,
        round: Int
    ): PhaseTransitionResult = mutate {
        val current = getEffectiveStateInternal()

        if (sessionId in explicitlyStoppedSessions || stopIntentStore.isRecoveryBlocked(sessionId)) {
            scheduler.cancelSessionAlarms(sessionId)
            return@mutate PhaseTransitionResult.Ignored("通知所属会话已停止，忽略晚到广播")
        }

        // 0. 停止意图检查：若该会话已在停止黑名单中，或当前已主动停止，坚决忽略
        if (sessionId in explicitlyStoppedSessions || (isExplicitlyStopped && current.status == TimerStatus.STOPPED)) {
            return@mutate PhaseTransitionResult.Ignored("该会话已被用户主动停止，忽略晚到广播")
        }

        // 1. 会话与 generation 校验：丢弃旧事件或已取消的在途事件
        if (current.sessionId != sessionId || current.generation != generation) {
            return@mutate PhaseTransitionResult.Ignored("事件校验失败：会话或 generation 不匹配，已安全忽略")
        }

        // 2. 状态与阶段校验：只有 RUNNING 状态且阶段匹配才接收切换
        if (current.status != TimerStatus.RUNNING) {
            return@mutate PhaseTransitionResult.Ignored("当前状态为 ${current.status}，非运行态，已忽略")
        }

        if (current.phase != expectedPhase) {
            return@mutate PhaseTransitionResult.Ignored("期望阶段 $expectedPhase 与当前阶段 ${current.phase} 不一致，已忽略")
        }

        if (current.currentRound != round) {
            return@mutate PhaseTransitionResult.Ignored("轮数不匹配，已忽略")
        }

        val nowElapsed = clockProvider.elapsedRealtime()
        val nowWall = clockProvider.currentTimeMillis()

        // 3. 提前到达防护：若广播异常提前到达且剩余时间超过 1 秒，重新安排剩余时间
        val prematureDiff = current.deadlineElapsedRealtimeMs - nowElapsed
        if (prematureDiff > 1000L) {
            scheduler.cancelPhaseAlarm(current.generation)
            val rescheduled = scheduler.schedulePhaseAlarm(current)
            return@mutate if (rescheduled) {
                PhaseTransitionResult.PrematureHandled("闹钟提前到达超过1秒，已重新对齐注册")
            } else {
                val errorState = current.copy(
                    status = TimerStatus.ERROR,
                    failureReason = TimerFailure.ALARM_SCHEDULING,
                    pausedRemainingMs = prematureDiff,
                    deadlineElapsedRealtimeMs = 0L,
                    deadlineWallClockMs = 0L,
                    errorMessage = "提前到达且重新调度失败，已安全暂停",
                    lastEventResult = "提前到达重调度失败"
                )
                updateEffectiveState(errorState)
                runCatching { repository.updateTimerState { errorState } }
                notifier.showErrorNotification("计时异常：提前到达调度失败", sessionId)
                notifier.showStatusNotification(errorState)
                PhaseTransitionResult.Failure("提前到达重新注册失败", errorState)
            }
        }

        // 4. 计算下一阶段与轮数
        val (nextPhase, nextRound, nextDurationMinutes) = TimerTransitions.nextPhase(current)
        val nextDurationMs = nextDurationMinutes * 60_000L
        val nextGeneration = current.generation + 1L

        val nextState = current.copy(
            generation = nextGeneration,
            status = TimerStatus.RUNNING,
            phase = nextPhase,
            currentRound = nextRound,
            phaseTotalDurationMs = nextDurationMs,
            deadlineElapsedRealtimeMs = nowElapsed + nextDurationMs,
            deadlineWallClockMs = nowWall + nextDurationMs,
            pausedRemainingMs = 0L,
            lastEventId = "PHASE_${nextGeneration}",
            lastEventResult = "成功进入 $nextPhase (第 $nextRound 轮)",
            lastEventTimestampMs = nowWall,
            failureReason = null,
            errorMessage = null
        )

        // 5. 严格清理旧 generation 闹钟，调度下一阶段闹钟
        scheduler.cancelPhaseAlarm(current.generation)
        val scheduled = scheduler.schedulePhaseAlarm(nextState, previousGeneration = current.generation)
        if (!scheduled) {
            scheduler.cancelPhaseAlarm(nextGeneration)
            val failureState = nextState.copy(
                status = TimerStatus.ERROR,
                failureReason = TimerFailure.ALARM_SCHEDULING,
                pausedRemainingMs = nextDurationMs,
                deadlineElapsedRealtimeMs = 0L,
                deadlineWallClockMs = 0L,
                errorMessage = "本阶段已结束，但下一阶段系统闹钟排程失败，计时已暂停",
                lastEventResult = "下一阶段排程失败，已暂停"
            )
            updateEffectiveState(failureState)
            runCatching { repository.updateTimerState { failureState } }
            notifier.showErrorNotification("下一阶段调度失败，计时已暂停", sessionId)
            notifier.showStatusNotification(failureState)
            return@mutate PhaseTransitionResult.Failure("下一阶段系统闹钟排程失败", failureState)
        }

        // 6. 排程成功后，执行有界重试持久化状态
        val saveResult = safeUpdateStateWithRetry(maxAttempts = 2) { nextState }
        if (saveResult.isSuccess) {
            val saved = saveResult.getOrThrow()
            updateEffectiveState(saved)
            notifier.showPhaseReminder(
                phase = nextPhase,
                round = nextRound,
                durationMinutes = nextDurationMinutes,
                sessionId = sessionId,
                generation = nextGeneration
            )
            notifier.showStatusNotification(saved)
            return@mutate PhaseTransitionResult.Success(saved)
        }

        // 持久化持续失败防御：撤回已注册闹钟，避免闹钟在跑但仓库滞后的脱节状态
        scheduler.cancelPhaseAlarm(nextGeneration)
        val errorState = nextState.copy(
            status = TimerStatus.ERROR,
            failureReason = TimerFailure.STORAGE_WRITE,
            pausedRemainingMs = nextDurationMs,
            deadlineElapsedRealtimeMs = 0L,
            deadlineWallClockMs = 0L,
            errorMessage = "存储故障，计时已中断",
            lastEventResult = "阶段状态落盘失败"
        )
        updateEffectiveState(errorState)
        runCatching { repository.updateTimerState { errorState } }
        notifier.showErrorNotification("阶段状态保存失败，计时已中断", sessionId)
        notifier.showStatusNotification(errorState)
        return@mutate PhaseTransitionResult.Failure("阶段状态保存失败", errorState)
    }

    /**
     * 系统生命周期事件（开机、系统时间调整、时区变化、安装更新、精确闹钟权限恢复）对账处理
     */
    suspend fun onSystemEvent(reason: TimerSystemEvent): TimerState = mutate {
        val current = getEffectiveStateInternal()
        if (stateReadFailed) return@mutate current

        // 0. 停止意图守护：若用户已主动停止，即便由于历史存储失败导致持久化层读出旧 RUNNING，也严禁自动恢复
        if (isExplicitlyStopped || current.status == TimerStatus.STOPPED || (current.sessionId.isNotEmpty() && current.sessionId in explicitlyStoppedSessions)) {
            scheduler.cancelAllPhaseAlarms()
            val stopped = current.copy(
                sessionId = "",
                status = TimerStatus.STOPPED,
                deadlineElapsedRealtimeMs = 0L,
                deadlineWallClockMs = 0L,
                pausedRemainingMs = 0L,
                failureReason = null,
                errorMessage = null
            )
            updateEffectiveState(stopped)
            runCatching { repository.updateTimerState { stopped } }
            return@mutate stopped
        }

        // 权限恢复事件处理：若之前因缺少权限处于 ERROR，立即自动重试恢复
        if (reason == TimerSystemEvent.EXACT_ALARM_PERMISSION_CHANGED) {
            if (current.status == TimerStatus.ERROR &&
                TimerRecoveryPolicy.canRetryAfterPermissionGrant(current, scheduler.canScheduleExactAlarms())) {
                val retryResult = resumeLocked(current)
                return@mutate retryResult.getOrElse { getEffectiveStateInternal() }
            }
            if (current.status != TimerStatus.RUNNING) return@mutate current
        }

        if (current.status != TimerStatus.RUNNING) {
            return@mutate current
        }

        val nowElapsed = clockProvider.elapsedRealtime()
        val nowWall = clockProvider.currentTimeMillis()

        // 取消旧闹钟，保证对账后仅有唯一有效闹钟
        scheduler.cancelPhaseAlarm(current.generation)

        // 根据事件类型决定剩余时长的基准参考系：
        // 1. 真实系统重启：单调时钟归零，以 wallClock 为准对账
        // 2. 同一次开机：单调时钟 elapsedRealtime 绝对单调递增，必须严格以 elapsedRealtime 计算剩余时间！
        val currentBootCount = clockProvider.bootCount()
        val timing = TimerRecoveryPolicy.timing(current, reason, nowElapsed, nowWall, currentBootCount)
        val isTrueBoot = timing.isTrueBoot
        val remainingMs = timing.remainingMs

        if (!timing.needsConfirmation) {
            val nextGeneration = current.generation + 1L
            val reconciledState = current.copy(
                generation = nextGeneration,
                deadlineElapsedRealtimeMs = nowElapsed + remainingMs,
                deadlineWallClockMs = nowWall + remainingMs,
                pausedRemainingMs = 0L,
                bootIdentifier = if (isTrueBoot) nowElapsed else current.bootIdentifier,
                bootCount = currentBootCount,
                lastEventResult = "系统事件($reason)对账完成，已重新排程",
                lastEventTimestampMs = nowWall,
                failureReason = null,
                errorMessage = null
            )
            val scheduled = scheduler.schedulePhaseAlarm(reconciledState, previousGeneration = current.generation)
            if (scheduled) {
                val saveResult = safeUpdateStateWithRetry(maxAttempts = 2) { reconciledState }
                if (saveResult.isSuccess) {
                    val saved = saveResult.getOrThrow()
                    updateEffectiveState(saved)
                    notifier.showStatusNotification(saved)
                    return@mutate saved
                } else {
                    scheduler.cancelPhaseAlarm(nextGeneration)
                    val errorState = reconciledState.copy(
                        status = TimerStatus.ERROR,
                        failureReason = TimerFailure.STORAGE_WRITE,
                        pausedRemainingMs = remainingMs,
                        deadlineElapsedRealtimeMs = 0L,
                        deadlineWallClockMs = 0L,
                        errorMessage = "存储故障，计时已中断",
                        lastEventResult = "对账落盘失败"
                    )
                    updateEffectiveState(errorState)
                    runCatching { repository.updateTimerState { errorState } }
                    notifier.showStatusNotification(errorState)
                    return@mutate errorState
                }
            } else {
                scheduler.cancelPhaseAlarm(nextGeneration)
                val errorState = reconciledState.copy(
                    status = TimerStatus.ERROR,
                    failureReason = TimerFailure.ALARM_SCHEDULING,
                    pausedRemainingMs = remainingMs,
                    deadlineElapsedRealtimeMs = 0L,
                    deadlineWallClockMs = 0L,
                    errorMessage = "因系统事件($reason)重新调度闹钟失败",
                    lastEventResult = "对账排程失败，已转为错误暂停"
                )
                updateEffectiveState(errorState)
                runCatching { repository.updateTimerState { errorState } }
                notifier.showErrorNotification("计时已暂停：系统调度失败", current.sessionId)
                notifier.showStatusNotification(errorState)
                return@mutate errorState
            }
        } else {
            // 当前阶段已自然到期或已超时：转入暂停状态或等待用户确认
            val nextGeneration = current.generation + 1L
            val expiredState = current.copy(
                generation = nextGeneration,
                status = TimerStatus.PAUSED,
                pausedRemainingMs = 0L,
                deadlineElapsedRealtimeMs = 0L,
                deadlineWallClockMs = 0L,
                bootIdentifier = if (isTrueBoot) nowElapsed else current.bootIdentifier,
                bootCount = currentBootCount,
                lastEventResult = "系统事件($reason)对账：阶段已到期",
                lastEventTimestampMs = nowWall,
                failureReason = null,
                errorMessage = null
            )
            val saveResult = safeUpdateStateWithRetry(maxAttempts = 2) { expiredState }
            val finalState = if (saveResult.isSuccess) saveResult.getOrThrow() else expiredState
            updateEffectiveState(finalState)
            notifier.showStatusNotification(finalState)
            return@mutate finalState
        }
    }
}
