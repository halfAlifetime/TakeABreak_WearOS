package com.takeabreak.wearos.notification

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// Resolve the current display settings on each call: the notifier can outlive a
// system time-zone change. A local formatter also avoids sharing mutable state.
internal fun formatNotificationDeadline(
    deadlineWallClockMs: Long,
    timeZone: TimeZone = TimeZone.getDefault(),
    locale: Locale = Locale.getDefault()
): String {
    if (deadlineWallClockMs <= 0L) return "--:--"
    return SimpleDateFormat("HH:mm", locale).apply {
        this.timeZone = timeZone
    }.format(Date(deadlineWallClockMs))
}
