package com.takeabreak.wearos.permission.support

import com.takeabreak.wearos.permission.ReminderCapabilities
import com.takeabreak.wearos.permission.ChannelCapability
import com.takeabreak.wearos.permission.ChannelImportance
import com.takeabreak.wearos.permission.InterruptionMode

fun readyCapabilities() = ReminderCapabilities(
    exactAlarmsAllowed = true, notificationsEnabled = true, postNotificationsGranted = true,
    workChannel = ChannelCapability(true, ChannelImportance.HIGH, true, false),
    breakChannel = ChannelCapability(true, ChannelImportance.HIGH, true, false),
    statusChannel = ChannelCapability(true, ChannelImportance.LOW, false, false),
    interruptionMode = InterruptionMode.ALL, vibratorAvailable = true, fullScreenIntentAllowed = true
)
