package com.batteryfitness.app.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * App background: an original athletics motif drawn as vectors.
 *
 * Deliberately NOT a bitmap and deliberately NOT anyone's official emblem.
 * Drawing it means no raster asset in the APK, correct rendering at every
 * density, and no third-party imagery whose licensing or implied endorsement we
 * would have to answer for.
 *
 * The motif is concentric running-track lanes sweeping from the lower corner,
 * with a tricolour accent — evocative of the programme without borrowing its
 * identity.
 *
 * Contrast is the functional constraint here, not decoration: Concept Note §10
 * requires the UI to be readable "in low light and direct sunlight", so every
 * motif element sits at low alpha behind content and never competes with text.
 */

val Saffron = Color(0xFFFF9933)
val IndiaGreen = Color(0xFF138808)

@Composable
fun AppBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val dark = isSystemInDarkTheme()

    val gradient = if (dark) {
        Brush.verticalGradient(
            listOf(Color(0xFF0B1220), Color(0xFF121A28), Color(0xFF0E1116)),
        )
    } else {
        Brush.verticalGradient(
            listOf(Color(0xFFF7F9FC), Color(0xFFEDF2FA), Color(0xFFF7F9FC)),
        )
    }

    // Lane strokes: light navy on a light background, pale navy on dark.
    val laneColor = if (dark) Color(0xFF4A6BA0) else Color(0xFF1B3A6B)
    val laneAlpha = if (dark) 0.13f else 0.07f

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(brush = gradient, size = size)

            // Concentric track lanes sweeping out of the bottom-left corner.
            val origin = Offset(-size.width * 0.15f, size.height * 1.05f)
            val lanes = 7
            val step = size.width * 0.19f
            repeat(lanes) { i ->
                val r = step * (i + 2)
                drawCircle(
                    color = laneColor.copy(alpha = laneAlpha),
                    radius = r,
                    center = origin,
                    style = Stroke(width = size.width * 0.012f),
                )
            }

            // A second, tighter arc set from the top-right for balance.
            val origin2 = Offset(size.width * 1.12f, -size.height * 0.06f)
            repeat(4) { i ->
                drawCircle(
                    color = laneColor.copy(alpha = laneAlpha * 0.75f),
                    radius = step * (i + 1.5f),
                    center = origin2,
                    style = Stroke(width = size.width * 0.010f),
                )
            }

            // Tricolour accent along the top edge — a thin national cue that
            // stays out of the content area.
            val barH = size.height * 0.0045f
            val third = size.width / 3f
            drawRect(Saffron.copy(alpha = 0.85f), Offset.Zero, Size(third, barH))
            drawRect(
                (if (dark) Color.White else Color(0xFFDDE3EC)).copy(alpha = 0.85f),
                Offset(third, 0f), Size(third, barH),
            )
            drawRect(
                IndiaGreen.copy(alpha = 0.85f),
                Offset(third * 2, 0f), Size(third, barH),
            )
        }
        content()
    }
}
