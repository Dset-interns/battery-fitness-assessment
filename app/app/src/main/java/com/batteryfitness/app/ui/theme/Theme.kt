package com.batteryfitness.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * High-contrast palette.
 *
 * Concept Note §10 (Usability & Access) requires the UI to be "readable in low
 * light and direct sunlight" — a PE teacher on a dusty ground at midday. That
 * rules out the low-contrast pastels a default Material palette would give, so
 * contrast is chosen deliberately rather than inherited.
 */
private val Navy = Color(0xFF1B3A6B)
private val NavyLight = Color(0xFF4A6BA0)
private val Amber = Color(0xFFB26A00)
private val Ink = Color(0xFF11151C)
private val Paper = Color(0xFFFCFCFD)

private val LightColors = lightColorScheme(
    primary = Navy,
    onPrimary = Color.White,
    secondary = Amber,
    onSecondary = Color.White,
    background = Paper,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
)

private val DarkColors = darkColorScheme(
    primary = NavyLight,
    onPrimary = Color.Black,
    secondary = Color(0xFFFFB74D),
    onSecondary = Color.Black,
    background = Color(0xFF0E1116),
    onBackground = Color(0xFFE6E8EB),
    surface = Color(0xFF171B21),
    onSurface = Color(0xFFE6E8EB),
)

@Composable
fun BatteryFitnessTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
