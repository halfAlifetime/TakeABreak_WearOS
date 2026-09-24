package com.takeabreak.wearos.notification

import java.time.Instant
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationTimeFormatterTest {
    @Test fun deadlineTextTracksDefaultTimeZoneChangesWithinTheSameProcess() {
        val originalTimeZone = TimeZone.getDefault()
        val deadline = Instant.parse("2026-09-24T12:00:00Z").toEpochMilli()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            assertEquals("12:00", formatNotificationDeadline(deadline, locale = Locale.US))

            TimeZone.setDefault(TimeZone.getTimeZone("GMT+08:00"))
            assertEquals("20:00", formatNotificationDeadline(deadline, locale = Locale.US))

            // Repeated changes must also work, including a date rollover.
            TimeZone.setDefault(TimeZone.getTimeZone("GMT+14:00"))
            assertEquals("02:00", formatNotificationDeadline(deadline, locale = Locale.US))
        } finally {
            TimeZone.setDefault(originalTimeZone)
        }
    }

    @Test fun missingDeadlineKeepsPlaceholderInEveryTimeZone() {
        for (zone in listOf("UTC", "GMT+08:00", "GMT-05:00")) {
            val timeZone = TimeZone.getTimeZone(zone)
            assertEquals("--:--", formatNotificationDeadline(0L, timeZone, Locale.US))
            assertEquals("--:--", formatNotificationDeadline(-1L, timeZone, Locale.US))
        }
    }
}
