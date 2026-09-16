package com.takeabreak.wearos.ui

import com.takeabreak.wearos.permission.SettingTarget

enum class FeedbackType {
    ERROR,
    SUCCESS,
    INFO
}

data class ActionFeedback(
    val message: String,
    val type: FeedbackType = FeedbackType.ERROR,
    val settingTarget: SettingTarget = SettingTarget.NONE
)
