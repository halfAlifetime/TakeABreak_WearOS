package com.takeabreak.wearos.ui

import com.takeabreak.wearos.permission.SettingTarget

enum class FeedbackType {
    ERROR,
    SUCCESS,
    INFO
}

/** Identifies the operation that owns a message, independently of its wording. */
enum class FeedbackSource {
    TIMER, WORK_DURATION, BREAK_DURATION, REMINDER_TEST, SETTINGS_NAVIGATION;

    val isDurationSetting: Boolean
        get() = this == WORK_DURATION || this == BREAK_DURATION
}

data class ActionFeedback(
    val id: Long,
    val message: String,
    val type: FeedbackType = FeedbackType.ERROR,
    val settingTarget: SettingTarget = SettingTarget.NONE,
    val source: FeedbackSource = FeedbackSource.TIMER
)
