package com.takeabreak.wearos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Icon
import com.takeabreak.wearos.ui.theme.DangerRedContainer
import com.takeabreak.wearos.ui.theme.DarkBackground
import com.takeabreak.wearos.ui.theme.FocusMintPrimary
import com.takeabreak.wearos.ui.theme.FocusMintText
import com.takeabreak.wearos.ui.theme.WarningRed

@Composable
internal fun BoxScope.TimerSettingsButton(borderColor: Color, tint: Color, onOpenSettings: () -> Unit) {
    // 顶部 12 点钟：设置齿轮
    Box(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = 18.dp)
            .size(34.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.10f))
            .border(1.dp, borderColor, CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onOpenSettings
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Settings,
            contentDescription = "设置",
            tint = tint,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
internal fun BoxScope.TimerRunningControls(activeColor: Color, activeTextColor: Color, onPause: () -> Unit, onAskStop: () -> Unit) {
    // 底部 6 点钟：对称双圆按键
    Row(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 暂停按键
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(activeColor.copy(alpha = 0.22f))
                .border(1.5.dp, activeColor.copy(alpha = 0.55f), CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onPause
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Pause,
                contentDescription = "暂停",
                tint = activeTextColor,
                modifier = Modifier.size(20.dp)
            )
        }

        // 结束重置按键
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(DangerRedContainer)
                .border(1.5.dp, WarningRed.copy(alpha = 0.50f), CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onAskStop
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Stop,
                contentDescription = "结束",
                tint = WarningRed,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
internal fun BoxScope.TimerPausedControls(onResume: () -> Unit, onAskStop: () -> Unit) {
    // 底部 6 点钟：继续 / 结束
    Row(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 继续按键
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(FocusMintPrimary.copy(alpha = 0.25f))
                .border(1.5.dp, FocusMintPrimary.copy(alpha = 0.60f), CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onResume
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "继续",
                tint = FocusMintText,
                modifier = Modifier.size(24.dp)
            )
        }

        // 结束重置按键
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(DangerRedContainer)
                .border(1.5.dp, WarningRed.copy(alpha = 0.50f), CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onAskStop
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Stop,
                contentDescription = "结束",
                tint = WarningRed,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
internal fun BoxScope.TimerStartControl(onStart: () -> Unit) {
    // 底部 6 点钟：开始大按键
    Box(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 18.dp)
            .size(48.dp)
            .clip(CircleShape)
            .background(FocusMintPrimary)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onStart
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = "开始",
            tint = DarkBackground,
            modifier = Modifier.size(26.dp)
        )
    }
}

@Composable
internal fun BoxScope.TimerErrorControls(onRetry: () -> Unit, onAskStop: () -> Unit) {
    Row(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(FocusMintPrimary.copy(alpha = 0.25f))
                .clickable(onClick = onRetry),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "重试",
                tint = FocusMintText,
                modifier = Modifier.size(20.dp)
            )
        }

        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(DangerRedContainer)
                .clickable(onClick = onAskStop),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Stop,
                contentDescription = "重置",
                tint = WarningRed,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
