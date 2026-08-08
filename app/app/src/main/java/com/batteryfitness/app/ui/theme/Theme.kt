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

// The container roles must be set explicitly. Material 3 falls back to its
// baseline purple for any role left undefined, which is how selected chips and
// completed rows ended up lavender rather than navy on the first device run.
private val LightColors = lightColorScheme(
    primary = Navy,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E2F5),
    onPrimaryContainer = Color(0xFF0C1E3C),
    secondary = Amber,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE2B8),
    onSecondaryContainer = Color(0xFF3D2200),
    background = Paper,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFEDEFF3),
    onSurfaceVariant = Color(0xFF3A414B),
    outline = Color(0xFF6B7280),
    outlineVariant = Color(0xFFC9CDD4),
    error = Color(0xFFB3261E),
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = NavyLight,
    onPrimary = Color(0xFF07142A),
    primaryContainer = Color(0xFF23375C),
    onPrimaryContainer = Color(0xFFD6E2F5),
    secondary = Color(0xFFFFB74D),
    onSecondary = Color(0xFF2A1800),
    secondaryContainer = Color(0xFF5A3A00),
    onSecondaryContainer = Color(0xFFFFE2B8),
    background = Color(0xFF0E1116),
    onBackground = Color(0xFFE6E8EB),
    surface = Color(0xFF171B21),
    onSurface = Color(0xFFE6E8EB),
    surfaceVariant = Color(0xFF262B33),
    onSurfaceVariant = Color(0xFFC3C8D0),
    outline = Color(0xFF8A919C),
    outlineVariant = Color(0xFF3A414B),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
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
