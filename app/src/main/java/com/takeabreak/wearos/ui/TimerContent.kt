package com.takeabreak.wearos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.takeabreak.wearos.ui.theme.FocusMintPrimary
import com.takeabreak.wearos.ui.theme.FocusMintText
import com.takeabreak.wearos.ui.theme.PauseGray
import com.takeabreak.wearos.ui.theme.TextPrimary
import com.takeabreak.wearos.ui.theme.TextSecondary
import com.takeabreak.wearos.ui.theme.WarningRed
import com.takeabreak.wearos.ui.theme.DarkBackground

@Composable
internal fun TimerLoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize().background(DarkBackground),
        contentAlignment = Alignment.Center
    ) {
        Text("正在读取计时…", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
    }
}

/**
 * 运行中状态：组合文字与操作控件
 * 12点钟: ⚙️ 极简设置按钮
 * 中央: 超大轻量细线等宽倒计时 + 纯净副标「专注中 / 放松中」
 * 6点钟: 对称双圆按键 (暂停 + 结束重置)
 */
@Composable
internal fun RunningZenView(
    isFocus: Boolean,
    timeString: String,
    activeColor: Color,
    activeTextColor: Color,
    onPause: () -> Unit,
    onAskStop: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val subText = if (isFocus) "专注中" else "放松中"

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        TimerSettingsButton(activeColor.copy(alpha = 0.35f), activeTextColor, onOpenSettings)

        // 中间倒计时 + 极简副标 (超大轻量细线等宽数字)
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(bottom = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = timeString,
                fontSize = 50.sp,
                fontWeight = FontWeight.ExtraLight,
                fontFamily = FontFamily.SansSerif,
                color = TextPrimary,
                letterSpacing = 1.5.sp
            )
            Text(
                text = subText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = activeTextColor,
                letterSpacing = 4.sp
            )
        }

        TimerRunningControls(activeColor, activeTextColor, onPause, onAskStop)
    }
}

/**
 * 暂停状态
 */
@Composable
internal fun PausedZenView(
    isFocus: Boolean,
    timeString: String,
    onResume: () -> Unit,
    onAskStop: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val subText = if (isFocus) "专注已暂停" else "放松已暂停"

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        TimerSettingsButton(Color.White.copy(alpha = 0.20f), TextSecondary, onOpenSettings)

        // 中间倒计时 + 暂停副标
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(bottom = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = timeString,
                fontSize = 50.sp,
                fontWeight = FontWeight.ExtraLight,
                fontFamily = FontFamily.SansSerif,
                color = TextPrimary.copy(alpha = 0.65f),
                letterSpacing = 1.5.sp
            )
            Text(
                text = subText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                color = PauseGray,
                letterSpacing = 3.sp
            )
        }

        TimerPausedControls(onResume, onAskStop)
    }
}

/**
 * 准备就绪 / 待机状态
 */
@Composable
internal fun StoppedZenView(
    workMin: Int,
    breakMin: Int,
    onStart: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        TimerSettingsButton(FocusMintPrimary.copy(alpha = 0.35f), FocusMintText, onOpenSettings)

        // 中间大号预设时长
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(bottom = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "%02d:00".format(workMin),
                fontSize = 50.sp,
                fontWeight = FontWeight.ExtraLight,
                fontFamily = FontFamily.SansSerif,
                color = TextPrimary,
                letterSpacing = 1.5.sp
            )
            Text(
                text = "休息 %d分钟".format(breakMin),
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                color = FocusMintText,
                letterSpacing = 3.sp
            )
        }

        TimerStartControl(onStart)
    }
}

@Composable
internal fun ErrorZenView(
    errorMessage: String,
    onRetry: () -> Unit,
    onAskStop: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "计时中断",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = WarningRed
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        }

        TimerErrorControls(onRetry, onAskStop)
    }
}
