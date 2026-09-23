package com.takeabreak.wearos.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.takeabreak.wearos.notification.ReminderSelfTest
import com.takeabreak.wearos.permission.ReminderCapabilityReader
import com.takeabreak.wearos.timer.ClockProvider
import com.takeabreak.wearos.timer.TimerEngine

class TimerViewModelFactory(
    private val engine: TimerEngine,
    private val clock: ClockProvider,
    private val capabilities: ReminderCapabilityReader,
    private val reminderSelfTest: ReminderSelfTest
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass == TimerViewModel::class.java) { "Unsupported ViewModel: ${modelClass.name}" }
        @Suppress("UNCHECKED_CAST")
        return TimerViewModel(engine, clock, capabilities, reminderSelfTest) as T
    }
}
