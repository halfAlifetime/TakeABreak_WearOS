package com.takeabreak.wearos.permission

sealed class PreflightCheckResult {
    object Passed : PreflightCheckResult()
    data class Blocked(val reason: String, val target: SettingTarget) : PreflightCheckResult()
}

/** Notification commands use the same policy as the UI; session validation stays in the engine. */
object ReminderPermissionChecker {
    fun checkPreflight(capabilities: ReminderCapabilities): PreflightCheckResult {
        val issue = ReminderPolicy.command(capabilities).blocker ?: return PreflightCheckResult.Passed
        return PreflightCheckResult.Blocked(ReminderMessages.describe(issue), issue.target)
    }
}
