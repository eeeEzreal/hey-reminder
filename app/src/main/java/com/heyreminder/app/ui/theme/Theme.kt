package com.heyreminder.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Signal,
    onPrimary = WarmWhite,
    primaryContainer = SignalSoft,
    background = Paper,
    onBackground = Ink,
    surface = WarmWhite,
    onSurface = Ink,
    onSurfaceVariant = Muted,
)

private val DarkColors = darkColorScheme(
    primary = ColorTokens.signalDark,
    onPrimary = Ink,
    background = Ink,
    surface = NightSurface,
)

private object ColorTokens {
    val signalDark = androidx.compose.ui.graphics.Color(0xFFFFB4A3)
}

@Composable
fun HeyReminderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && darkTheme -> {
            dynamicDarkColorScheme(LocalContext.current)
        }
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            dynamicLightColorScheme(LocalContext.current)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}

