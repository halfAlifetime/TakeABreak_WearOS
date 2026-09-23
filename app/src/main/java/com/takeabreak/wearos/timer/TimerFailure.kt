package com.takeabreak.wearos.timer

/** Stable persisted codes. Human-readable messages must never select a recovery path. */
enum class TimerFailure(val storageCode: String) {
    STORAGE_READ("storage_read"),
    STORAGE_WRITE("storage_write"),
    ALARM_SCHEDULING("alarm_scheduling"),
    UNKNOWN("unknown");

    companion object {
        fun fromPersisted(status: TimerStatus, code: String?): TimerFailure? {
            if (status != TimerStatus.ERROR) return null
            return entries.firstOrNull { it.storageCode == code } ?: UNKNOWN
        }
    }
}
