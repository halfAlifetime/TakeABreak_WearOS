package com.takeabreak.wearos.permission

import com.takeabreak.wearos.timer.TimerPhase

/** Presentation mapping only. Business decisions never inspect these strings. */
object ReminderMessages {
    fun describe(issue: CapabilityIssue): String {
        val phase = when (issue.phase) { TimerPhase.WORK -> "工作"; TimerPhase.BREAK -> "休息"; null -> "" }
        return when (issue.code) {
            CapabilityIssueCode.READ_FAILED -> "暂时无法读取${phase}提醒能力，请重新检查"
            CapabilityIssueCode.EXACT_ALARM_DENIED -> "精确闹钟权限未开启，无法保证准时提醒"
            CapabilityIssueCode.NOTIFICATIONS_DISABLED -> "应用通知总开关已关闭，请在手表通知设置中开启"
            CapabilityIssueCode.NOTIFICATION_PERMISSION_DENIED -> "应用通知权限未授予，请允许通知"
            CapabilityIssueCode.CHANNEL_MISSING -> "${phase}提醒渠道未创建，请重新打开应用"
            CapabilityIssueCode.CHANNEL_DISABLED -> "${phase}提醒渠道已关闭，请在通知设置中开启"
            CapabilityIssueCode.CHANNEL_VIBRATION_DISABLED -> "${phase}提醒渠道振动已关闭"
            CapabilityIssueCode.CHANNEL_GROUP_BLOCKED -> "${phase}提醒所属通知分组已关闭"
            CapabilityIssueCode.CHANNEL_SILENT -> "${phase}提醒渠道已静音，当前不会请求振动"
            CapabilityIssueCode.DND_RESTRICTED -> "当前勿扰模式限制触感提醒，计时仍可继续"
            CapabilityIssueCode.NO_VIBRATOR -> "未检测到可用的手表振动器，计时仍可继续"
            CapabilityIssueCode.POPUP_IMPORTANCE_LOW -> "${phase}提醒渠道重要性不足，弹屏可能受限"
            CapabilityIssueCode.FULL_SCREEN_UNAVAILABLE -> "全屏提醒权限未开启，仍可显示普通通知"
            CapabilityIssueCode.POPUP_DND_RESTRICTED -> "勿扰模式下弹屏由系统决定"
        }
    }

    fun vibration(decision: CapabilityDecision): String =
        decision.blocker?.let(::describe) ?: "允许请求振动，请通过自检确认实际触感"

    fun command(decision: CapabilityDecision): String = decision.blocker?.let(::describe)
        ?: decision.warnings.joinToString("；", transform = ::describe).ifEmpty { "调度与提醒配置已就绪" }
}
