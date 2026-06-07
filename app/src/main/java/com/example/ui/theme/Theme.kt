package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = CyberDarkPrimary,
    secondary = CyberDarkSecondary,
    tertiary = CyberDarkTertiary,
    background = CyberDarkBackground,
    surface = CyberDarkSurface,
    surfaceVariant = CyberDarkSurfaceVariant,
    onBackground = CyberDarkTextPrimary,
    onSurface = CyberDarkTextPrimary,
    onSurfaceVariant = CyberDarkTextSecondary,
    onPrimary = CyberDarkBackground,
    onSecondary = CyberDarkBackground
)

private val LightColorScheme = lightColorScheme(
    primary = CleanLightPrimary,
    secondary = CleanLightSecondary,
    tertiary = CleanLightTertiary,
    background = CleanLightBackground,
    surface = CleanLightSurface,
    surfaceVariant = CleanLightSurfaceVariant,
    onBackground = CleanLightTextPrimary,
    onSurface = CleanLightTextPrimary,
    onSurfaceVariant = CleanLightTextSecondary,
    onPrimary = CleanLightSurface,
    onSecondary = CleanLightSurface
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
