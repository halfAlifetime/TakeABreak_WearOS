package com.takeabreak.wearos.permission

import com.takeabreak.wearos.timer.TimerPhase

/** One set of rules shared by diagnostics, command preflight and each motor request. */
object ReminderPolicy {
    private fun target(phase: TimerPhase) =
        if (phase == TimerPhase.WORK) SettingTarget.CHANNEL_WORK else SettingTarget.CHANNEL_BREAK

    private fun notificationIssue(c: ReminderCapabilities): CapabilityIssue? = when {
        c.notificationsEnabled == null || c.postNotificationsGranted == null ->
            CapabilityIssue(CapabilityIssueCode.READ_FAILED, SettingTarget.APP_NOTIFICATION)
        !c.notificationsEnabled -> CapabilityIssue(CapabilityIssueCode.NOTIFICATIONS_DISABLED, SettingTarget.APP_NOTIFICATION)
        !c.postNotificationsGranted -> CapabilityIssue(CapabilityIssueCode.NOTIFICATION_PERMISSION_DENIED, SettingTarget.APP_NOTIFICATION)
        else -> null
    }

    private fun channelIssue(c: ChannelCapability, phase: TimerPhase): CapabilityIssue? {
        val code = when {
            c.exists == null -> CapabilityIssueCode.READ_FAILED
            !c.exists -> CapabilityIssueCode.CHANNEL_MISSING
            c.importance == ChannelImportance.UNKNOWN || c.groupBlocked == null || c.vibrationEnabled == null -> CapabilityIssueCode.READ_FAILED
            c.groupBlocked -> CapabilityIssueCode.CHANNEL_GROUP_BLOCKED
            c.importance == ChannelImportance.NONE -> CapabilityIssueCode.CHANNEL_DISABLED
            !c.vibrationEnabled -> CapabilityIssueCode.CHANNEL_VIBRATION_DISABLED
            else -> return null
        }
        return CapabilityIssue(code, target(phase), phase)
    }

    fun vibration(c: ReminderCapabilities, phase: TimerPhase): CapabilityDecision {
        notificationIssue(c)?.let { return CapabilityDecision(it) }
        channelIssue(c.channel(phase), phase)?.let { return CapabilityDecision(it) }
        val issue = when {
            c.channel(phase).importance in setOf(ChannelImportance.MIN, ChannelImportance.LOW) ->
                CapabilityIssue(CapabilityIssueCode.CHANNEL_SILENT, target(phase), phase)
            c.interruptionMode == InterruptionMode.UNKNOWN || c.vibratorAvailable == null ->
                CapabilityIssue(CapabilityIssueCode.READ_FAILED)
            c.interruptionMode in setOf(InterruptionMode.PRIORITY, InterruptionMode.NONE) ->
                CapabilityIssue(CapabilityIssueCode.DND_RESTRICTED)
            !c.vibratorAvailable -> CapabilityIssue(CapabilityIssueCode.NO_VIBRATOR)
            else -> null
        }
        return CapabilityDecision(issue)
    }

    /** Popup eligibility is configuration only; the OS still decides actual presentation. */
    fun popup(c: ReminderCapabilities, phase: TimerPhase): CapabilityDecision {
        notificationIssue(c)?.let { return CapabilityDecision(it) }
        val channel = c.channel(phase)
        val issue = when {
            channel.exists == null ->
                CapabilityIssue(CapabilityIssueCode.READ_FAILED, target(phase), phase)
            !channel.exists -> CapabilityIssue(CapabilityIssueCode.CHANNEL_MISSING, target(phase), phase)
            channel.importance == ChannelImportance.UNKNOWN || channel.groupBlocked == null ->
                CapabilityIssue(CapabilityIssueCode.READ_FAILED, target(phase), phase)
            channel.groupBlocked -> CapabilityIssue(CapabilityIssueCode.CHANNEL_GROUP_BLOCKED, target(phase), phase)
            channel.importance != ChannelImportance.HIGH -> CapabilityIssue(CapabilityIssueCode.POPUP_IMPORTANCE_LOW, target(phase), phase)
            c.fullScreenIntentAllowed == null || c.interruptionMode == InterruptionMode.UNKNOWN -> CapabilityIssue(CapabilityIssueCode.READ_FAILED)
            !c.fullScreenIntentAllowed -> CapabilityIssue(CapabilityIssueCode.FULL_SCREEN_UNAVAILABLE, SettingTarget.APP_NOTIFICATION)
            c.interruptionMode != InterruptionMode.ALL -> CapabilityIssue(CapabilityIssueCode.POPUP_DND_RESTRICTED)
            else -> null
        }
        return CapabilityDecision(issue)
    }

    fun command(c: ReminderCapabilities): CapabilityDecision {
        when (c.exactAlarmsAllowed) {
            null -> return CapabilityDecision(CapabilityIssue(CapabilityIssueCode.READ_FAILED, SettingTarget.EXACT_ALARM))
            false -> return CapabilityDecision(CapabilityIssue(CapabilityIssueCode.EXACT_ALARM_DENIED, SettingTarget.EXACT_ALARM))
            true -> Unit
        }
        notificationIssue(c)?.let { return CapabilityDecision(it) }
        for (phase in TimerPhase.entries) {
            channelIssue(c.channel(phase), phase)?.let { return CapabilityDecision(it) }
        }
        if (c.interruptionMode == InterruptionMode.UNKNOWN || c.vibratorAvailable == null) {
            return CapabilityDecision(CapabilityIssue(CapabilityIssueCode.READ_FAILED))
        }
        // A temporary DND restriction or reduced importance does not stop the timer.
        val warnings = TimerPhase.entries.flatMap { phase ->
            listOfNotNull(vibration(c, phase).blocker, popup(c, phase).blocker)
        }.distinct()
        return CapabilityDecision(warnings = warnings)
    }

    fun diagnose(c: ReminderCapabilities) = ReminderDiagnostics(
        c, command(c), vibration(c, TimerPhase.WORK), vibration(c, TimerPhase.BREAK),
        popup(c, TimerPhase.WORK), popup(c, TimerPhase.BREAK),
        c.statusChannel.exists == true && c.statusChannel.groupBlocked == false &&
            c.statusChannel.importance !in setOf(ChannelImportance.NONE, ChannelImportance.UNKNOWN)
    )
}
