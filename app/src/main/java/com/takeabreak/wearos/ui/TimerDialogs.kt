package com.takeabreak.wearos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.takeabreak.wearos.permission.SettingTarget
import com.takeabreak.wearos.ui.theme.DarkBackground
import com.takeabreak.wearos.ui.theme.FocusMintPrimary
import com.takeabreak.wearos.ui.theme.FocusMintText
import com.takeabreak.wearos.ui.theme.TextPrimary
import com.takeabreak.wearos.ui.theme.TextSecondary
import com.takeabreak.wearos.ui.theme.WarningRed

@Composable
internal fun ActionFeedbackDialog(
    feedback: ActionFeedback,
    onDismiss: (Long) -> Unit,
    onOpenSettingTarget: (SettingTarget) -> Unit
) {
    val titleText = when (feedback.type) {
        FeedbackType.ERROR -> "提示"
        FeedbackType.SUCCESS -> "完成"
        FeedbackType.INFO -> "信息"
    }
    val titleColor = when (feedback.type) {
        FeedbackType.ERROR -> WarningRed
        FeedbackType.SUCCESS -> FocusMintPrimary
        FeedbackType.INFO -> FocusMintText
    }

    TimerModal(onDismissRequest = { onDismiss(feedback.id) }) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = titleText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = titleColor
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = feedback.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextPrimary,
                    textAlign = TextAlign.Center,
                    maxLines = 3
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (feedback.settingTarget != SettingTarget.NONE) {
                        Button(
                            onClick = {
                                onDismiss(feedback.id)
                                onOpenSettingTarget(feedback.settingTarget)
                            },
                            modifier = Modifier
                                .height(36.dp)
                                .padding(horizontal = 4.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = FocusMintPrimary,
                                contentColor = DarkBackground
                            )
                        ) {
                            Text(
                                text = "去设置",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    FilledTonalButton(
                        onClick = { onDismiss(feedback.id) },
                        modifier = Modifier
                            .height(36.dp)
                            .padding(horizontal = 4.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color.White.copy(alpha = 0.16f),
                            contentColor = TextPrimary
                        )
                    ) {
                        Text(
                            text = "好",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}

/**
 * 极简结束二次防误触确认弹窗
 */
@Composable
internal fun StopConfirmDialog(
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    TimerModal(onDismissRequest = onCancel) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.94f))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "结束计时",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = WarningRed
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "确认提前结束本次专注吗？",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = onCancel,
                        modifier = Modifier
                            .width(66.dp)
                            .height(36.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color.White.copy(alpha = 0.15f),
                            contentColor = TextPrimary
                        )
                    ) {
                        Text(
                            text = "取消",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }

                    Button(
                        onClick = onConfirm,
                        modifier = Modifier
                            .width(66.dp)
                            .height(36.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = WarningRed,
                            contentColor = TextPrimary
                        )
                    ) {
                        Text(
                            text = "结束",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}


@Composable
private fun TimerModal(onDismissRequest: () -> Unit, content: @Composable () -> Unit) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        ),
        content = content
    )
}
