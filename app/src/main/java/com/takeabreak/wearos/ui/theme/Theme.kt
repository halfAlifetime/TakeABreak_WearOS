package com.takeabreak.wearos.ui.theme

import androidx.compose.runtime.Composable
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme

val WearColorScheme = ColorScheme(
    primary = WorkBluePrimary,
    primaryContainer = WorkBlueContainer,
    onPrimary = DarkBackground,
    secondary = BreakGreenPrimary,
    secondaryContainer = BreakGreenContainer,
    onSecondary = DarkBackground,
    background = DarkBackground,
    onBackground = TextPrimary,
    surfaceContainer = SurfaceDark,
    onSurface = TextPrimary
)

@Composable
fun TakeABreakTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = WearColorScheme,
        typography = WearTypography,
        content = content
    )
}
