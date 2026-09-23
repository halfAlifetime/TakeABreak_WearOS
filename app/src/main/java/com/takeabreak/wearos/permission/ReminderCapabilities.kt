package com.takeabreak.wearos.permission

import com.takeabreak.wearos.timer.TimerPhase

enum class SettingTarget { NONE, EXACT_ALARM, APP_NOTIFICATION, CHANNEL_WORK, CHANNEL_BREAK, CHANNEL_STATUS }
enum class ChannelImportance { UNKNOWN, NONE, MIN, LOW, DEFAULT, HIGH }
enum class InterruptionMode { UNKNOWN, ALL, ALARMS, PRIORITY, NONE }

/** Null/UNKNOWN means a system value could not be read, never implicit permission. */
data class ChannelCapability(
    val exists: Boolean? = null,
    val importance: ChannelImportance = ChannelImportance.UNKNOWN,
    val vibrationEnabled: Boolean? = null,
    val groupBlocked: Boolean? = null
)

data class ReminderCapabilities(
    val exactAlarmsAllowed: Boolean? = null,
    val notificationsEnabled: Boolean? = null,
    val postNotificationsGranted: Boolean? = null,
    val workChannel: ChannelCapability = ChannelCapability(),
    val breakChannel: ChannelCapability = ChannelCapability(),
    val statusChannel: ChannelCapability = ChannelCapability(),
    val interruptionMode: InterruptionMode = InterruptionMode.UNKNOWN,
    val vibratorAvailable: Boolean? = null,
    val fullScreenIntentAllowed: Boolean? = null
) {
    fun channel(phase: TimerPhase): ChannelCapability =
        if (phase == TimerPhase.WORK) workChannel else breakChannel
}

enum class CapabilityIssueCode {
    READ_FAILED, EXACT_ALARM_DENIED, NOTIFICATIONS_DISABLED, NOTIFICATION_PERMISSION_DENIED,
    CHANNEL_MISSING, CHANNEL_DISABLED, CHANNEL_VIBRATION_DISABLED, CHANNEL_GROUP_BLOCKED,
    CHANNEL_SILENT, DND_RESTRICTED, NO_VIBRATOR, POPUP_IMPORTANCE_LOW,
    FULL_SCREEN_UNAVAILABLE, POPUP_DND_RESTRICTED
}

data class CapabilityIssue(
    val code: CapabilityIssueCode,
    val target: SettingTarget = SettingTarget.NONE,
    val phase: TimerPhase? = null
)

data class CapabilityDecision(
    val blocker: CapabilityIssue? = null,
    val warnings: List<CapabilityIssue> = emptyList()
) {
    val allowed: Boolean get() = blocker == null
}

data class ReminderDiagnostics(
    val capabilities: ReminderCapabilities,
    val command: CapabilityDecision,
    val workVibration: CapabilityDecision,
    val breakVibration: CapabilityDecision,
    val workPopup: CapabilityDecision,
    val breakPopup: CapabilityDecision,
    val statusChannelReady: Boolean
)
