package com.takeabreak.wearos

import android.app.Application
import com.takeabreak.wearos.alarm.AlarmScheduler
import com.takeabreak.wearos.alarm.AndroidAlarmScheduler
import com.takeabreak.wearos.notification.AndroidReminderNotifier
import com.takeabreak.wearos.notification.NotificationChannels
import com.takeabreak.wearos.notification.ReminderNotifier
import com.takeabreak.wearos.timer.ClockProvider
import com.takeabreak.wearos.timer.DataStoreTimerRepository
import com.takeabreak.wearos.timer.SystemClockProvider
import com.takeabreak.wearos.timer.TimerEngine
import com.takeabreak.wearos.timer.TimerRepository

class TakeABreakApplication : Application() {

    companion object {
        lateinit var instance: TakeABreakApplication
            private set
    }

    lateinit var clockProvider: ClockProvider
        private set

    lateinit var timerRepository: TimerRepository
        private set

    lateinit var alarmScheduler: AlarmScheduler
        private set

    lateinit var reminderNotifier: ReminderNotifier
        private set

    lateinit var timerEngine: TimerEngine
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        clockProvider = SystemClockProvider()
        timerRepository = DataStoreTimerRepository(this)
        alarmScheduler = AndroidAlarmScheduler(this, clockProvider)
        reminderNotifier = AndroidReminderNotifier(this)

        timerEngine = TimerEngine(
            repository = timerRepository,
            scheduler = alarmScheduler,
            notifier = reminderNotifier,
            clockProvider = clockProvider
        )

        // 初始化 Wear OS 系统通知渠道
        NotificationChannels.createChannels(this)
    }
}
