package com.takeabreak.wearos.permission

/** Reads a fresh snapshot. Unavailable fields are represented explicitly in the result. */
fun interface ReminderCapabilityReader {
    fun read(): ReminderCapabilities
}
