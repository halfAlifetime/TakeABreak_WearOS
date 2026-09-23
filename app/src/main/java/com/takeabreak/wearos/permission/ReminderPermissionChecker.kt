package com.takeabreak.wearos.permission

/** Notification commands use the same policy as the UI; session validation stays in the engine. */
object ReminderPermissionChecker {
    fun checkPreflight(capabilities: ReminderCapabilities): Result<Unit> {
        val issue = ReminderPolicy.command(capabilities).blocker ?: return Result.success(Unit)
        return Result.failure(IllegalStateException(ReminderMessages.describe(issue)))
    }
}
