package com.takeabreak.wearos

import android.app.Application
import com.takeabreak.wearos.ui.TimerViewModelFactory
import com.takeabreak.wearos.alarm.AndroidAlarmScheduler
import com.takeabreak.wearos.notification.AndroidReminderNotifier
import com.takeabreak.wearos.notification.NotificationChannels
import com.takeabreak.wearos.timer.DataStoreTimerRepository
import com.takeabreak.wearos.timer.SystemClockProvider
import com.takeabreak.wearos.timer.SharedPreferencesStopIntentStore
import com.takeabreak.wearos.timer.TimerEngine
import com.takeabreak.wearos.permission.AndroidReminderCapabilityReader
import com.takeabreak.wearos.permission.ReminderCapabilityReader

class TakeABreakApplication : Application() {

    companion object {
        lateinit var instance: TakeABreakApplication
            private set
    }

    lateinit var capabilityReader: ReminderCapabilityReader
        private set

    lateinit var timerEngine: TimerEngine
        private set

    lateinit var timerViewModelFactory: TimerViewModelFactory
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        val clockProvider = SystemClockProvider(this)
        val timerRepository = DataStoreTimerRepository(this)
        val alarmScheduler = AndroidAlarmScheduler(this, clockProvider)
        NotificationChannels.createChannels(this)
        capabilityReader = AndroidReminderCapabilityReader(this, alarmScheduler)
        val reminderNotifier = AndroidReminderNotifier(this, capabilityReader)

        timerEngine = TimerEngine(
            repository = timerRepository,
            scheduler = alarmScheduler,
            notifier = reminderNotifier,
            clockProvider = clockProvider,
            stopIntentStore = SharedPreferencesStopIntentStore(this)
        )
        timerViewModelFactory = TimerViewModelFactory(
            timerEngine, clockProvider, capabilityReader, reminderNotifier
        )

    }
}
