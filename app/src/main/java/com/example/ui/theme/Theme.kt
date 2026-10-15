package com.example.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = DarkNavyBackground,
    primaryContainer = DarkNavyElevated,
    onPrimaryContainer = NeonCyan,
    secondary = NeonViolet,
    onSecondary = TextPrimary,
    secondaryContainer = DarkNavyElevated,
    onSecondaryContainer = NeonViolet,
    tertiary = NeonRose,
    onTertiary = TextPrimary,
    background = DarkNavyBackground,
    onBackground = TextPrimary,
    surface = DarkNavySurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkNavyElevated,
    onSurfaceVariant = TextSecondary,
    outline = DarkNavyBorder,
    error = NeonRose,
    onError = TextPrimary
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Default to dark for immersive video chat
    dynamicColor: Boolean = false, // Keep intentional cyberpunk video theme
    content: @Composable () -> Unit
) {
    val colorScheme = DarkColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = DarkNavyBackground.toArgb()
                window.navigationBarColor = DarkNavyBackground.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
