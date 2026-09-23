package com.takeabreak.wearos.timer

/** Platform actions are decoded by SystemEventReceiver, outside the timer rules. */
enum class TimerSystemEvent {
    BOOT_COMPLETED, TIME_CHANGED, TIMEZONE_CHANGED, PACKAGE_REPLACED, EXACT_ALARM_PERMISSION_CHANGED
}
