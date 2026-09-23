package com.takeabreak.wearos.permission

import com.takeabreak.wearos.permission.support.readyCapabilities
import com.takeabreak.wearos.timer.TimerPhase
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class ReminderPolicyTest(private val case: Case) {
    data class Case(
        val label: String,
        val snapshot: ReminderCapabilities,
        val commandBlocked: CapabilityIssueCode?,
        val workVibrationBlocked: CapabilityIssueCode?
    ) { override fun toString() = label }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun cases(): List<Array<Case>> {
            val c = readyCapabilities()
            fun channel(value: ChannelCapability) = c.copy(workChannel = value)
            return listOf(
                Case("ready", c, null, null),
                Case("exact alarm denied", c.copy(exactAlarmsAllowed = false), CapabilityIssueCode.EXACT_ALARM_DENIED, null),
                Case("notifications disabled", c.copy(notificationsEnabled = false), CapabilityIssueCode.NOTIFICATIONS_DISABLED, CapabilityIssueCode.NOTIFICATIONS_DISABLED),
                Case("notification permission denied", c.copy(postNotificationsGranted = false), CapabilityIssueCode.NOTIFICATION_PERMISSION_DENIED, CapabilityIssueCode.NOTIFICATION_PERMISSION_DENIED),
                Case("channel missing", channel(ChannelCapability(exists = false)), CapabilityIssueCode.CHANNEL_MISSING, CapabilityIssueCode.CHANNEL_MISSING),
                Case("channel disabled", channel(c.workChannel.copy(importance = ChannelImportance.NONE)), CapabilityIssueCode.CHANNEL_DISABLED, CapabilityIssueCode.CHANNEL_DISABLED),
                Case("channel vibration off", channel(c.workChannel.copy(vibrationEnabled = false)), CapabilityIssueCode.CHANNEL_VIBRATION_DISABLED, CapabilityIssueCode.CHANNEL_VIBRATION_DISABLED),
                Case("group blocked", channel(c.workChannel.copy(groupBlocked = true)), CapabilityIssueCode.CHANNEL_GROUP_BLOCKED, CapabilityIssueCode.CHANNEL_GROUP_BLOCKED),
                Case("minimum importance", channel(c.workChannel.copy(importance = ChannelImportance.MIN)), null, CapabilityIssueCode.CHANNEL_SILENT),
                Case("low importance", channel(c.workChannel.copy(importance = ChannelImportance.LOW)), null, CapabilityIssueCode.CHANNEL_SILENT),
                Case("default importance", channel(c.workChannel.copy(importance = ChannelImportance.DEFAULT)), null, null),
                Case("alarm only DND", c.copy(interruptionMode = InterruptionMode.ALARMS), null, null),
                Case("priority DND", c.copy(interruptionMode = InterruptionMode.PRIORITY), null, CapabilityIssueCode.DND_RESTRICTED),
                Case("total DND", c.copy(interruptionMode = InterruptionMode.NONE), null, CapabilityIssueCode.DND_RESTRICTED),
                Case("no motor", c.copy(vibratorAvailable = false), null, CapabilityIssueCode.NO_VIBRATOR),
                Case("unreadable exact permission", c.copy(exactAlarmsAllowed = null), CapabilityIssueCode.READ_FAILED, null),
                Case("unreadable notifications", c.copy(notificationsEnabled = null), CapabilityIssueCode.READ_FAILED, CapabilityIssueCode.READ_FAILED),
                Case("unreadable channel", channel(ChannelCapability()), CapabilityIssueCode.READ_FAILED, CapabilityIssueCode.READ_FAILED),
                Case("unreadable group", channel(c.workChannel.copy(groupBlocked = null)), CapabilityIssueCode.READ_FAILED, CapabilityIssueCode.READ_FAILED),
                Case("unreadable DND", c.copy(interruptionMode = InterruptionMode.UNKNOWN), CapabilityIssueCode.READ_FAILED, CapabilityIssueCode.READ_FAILED),
                Case("unreadable motor", c.copy(vibratorAvailable = null), CapabilityIssueCode.READ_FAILED, CapabilityIssueCode.READ_FAILED),
                Case("full screen denied", c.copy(fullScreenIntentAllowed = false), null, null),
                Case("full screen unknown", c.copy(fullScreenIntentAllowed = null), null, null)
            ).map { arrayOf(it) }
        }
    }

    @Test fun commandMatchesExpectedContract() {
        assertEquals(case.commandBlocked, ReminderPolicy.command(case.snapshot).blocker?.code)
    }

    @Test fun directWorkVibrationMatchesExpectedContract() {
        assertEquals(case.workVibrationBlocked, ReminderPolicy.vibration(case.snapshot, TimerPhase.WORK).blocker?.code)
    }

    @Test fun diagnosticsAndPreflightExposeTheExecutionRestriction() {
        val diagnosis = ReminderPolicy.diagnose(case.snapshot)
        assertEquals(case.commandBlocked, diagnosis.command.blocker?.code)
        assertEquals(case.workVibrationBlocked, diagnosis.workVibration.blocker?.code)
        if (case.commandBlocked == null && case.workVibrationBlocked != null) {
            assertTrue(diagnosis.command.warnings.contains(diagnosis.workVibration.blocker))
        }
        assertEquals(case.commandBlocked == null, ReminderPermissionChecker.checkPreflight(case.snapshot).isSuccess)
    }
}

class ReminderPolicyBoundaryTest {
    @Test fun manualWorkDoesNotDependOnBreakChannel() {
        val c = readyCapabilities().copy(breakChannel = ChannelCapability(exists = false))
        assertTrue(ReminderPolicy.vibration(c, TimerPhase.WORK).allowed)
        assertEquals(SettingTarget.CHANNEL_BREAK, ReminderPolicy.command(c).blocker?.target)
        assertEquals(CapabilityIssueCode.CHANNEL_MISSING, ReminderPolicy.vibration(c, TimerPhase.BREAK).blocker?.code)
    }

    @Test fun bothPhasesUseTheSameChannelRules() {
        val c = readyCapabilities()
        for (importance in ChannelImportance.entries) {
            val work = c.copy(workChannel = c.workChannel.copy(importance = importance))
            val rest = c.copy(breakChannel = c.breakChannel.copy(importance = importance))
            assertEquals(ReminderPolicy.vibration(work, TimerPhase.WORK).blocker?.code, ReminderPolicy.vibration(rest, TimerPhase.BREAK).blocker?.code)
        }
    }

    @Test fun popupAndVibrationAreIndependentCapabilities() {
        val c = readyCapabilities()
        val lower = c.copy(workChannel = c.workChannel.copy(importance = ChannelImportance.DEFAULT))
        assertTrue(ReminderPolicy.vibration(lower, TimerPhase.WORK).allowed)
        assertEquals(CapabilityIssueCode.POPUP_IMPORTANCE_LOW, ReminderPolicy.popup(lower, TimerPhase.WORK).blocker?.code)
        val missing = c.copy(fullScreenIntentAllowed = false)
        assertTrue(ReminderPolicy.vibration(missing, TimerPhase.WORK).allowed)
        assertEquals(CapabilityIssueCode.FULL_SCREEN_UNAVAILABLE, ReminderPolicy.popup(missing, TimerPhase.WORK).blocker?.code)
        assertEquals(CapabilityIssueCode.POPUP_DND_RESTRICTED, ReminderPolicy.popup(c.copy(interruptionMode = InterruptionMode.ALARMS), TimerPhase.WORK).blocker?.code)
    }

    @Test fun statusChannelCannotClaimReadyWhenReadFailedOrBlocked() {
        val c = readyCapabilities()
        assertFalse(ReminderPolicy.diagnose(c.copy(statusChannel = ChannelCapability())).statusChannelReady)
        assertFalse(ReminderPolicy.diagnose(c.copy(statusChannel = c.statusChannel.copy(groupBlocked = true))).statusChannelReady)
        assertTrue(ReminderPolicy.diagnose(c).statusChannelReady)
    }
}
