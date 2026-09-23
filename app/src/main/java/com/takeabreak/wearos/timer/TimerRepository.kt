package com.takeabreak.wearos.timer

import kotlinx.coroutines.flow.Flow

/** Persistence only. Timer commands and the public effective state belong to TimerEngine. */
interface TimerRepository {
    val timerStateFlow: Flow<TimerState>
    suspend fun getTimerState(): TimerState
    suspend fun updateTimerState(transform: (TimerState) -> TimerState): TimerState
}
