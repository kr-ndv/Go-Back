package com.goback.focus.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Material 3 Expressive Linear Squiggly / Wavy Progress Indicator.
 * Conforms to the Material 3 Expressive specifications with dynamic spring
 * physics and continuous sinusoidal wave propagation.
 */
@Composable
fun M3SquigglyProgress(
    progress: Float = 1f,
    modifier: Modifier = Modifier,
    active: Boolean = true,
    indeterminate: Boolean = false,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    height: Dp = 16.dp,
    strokeWidth: Dp = 3.5.dp,
    amplitude: Dp = 3.5.dp,
    wavelength: Dp = 22.dp
) {
    val clampedProgress = progress.coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = clampedProgress,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "progress_anim"
    )

    // Continuous wave phase ripple when active
    val infiniteTransition = rememberInfiniteTransition(label = "wave_phase")
    val phaseOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        val strokePx = strokeWidth.toPx()
        val ampPx = amplitude.toPx()
        val waveLenPx = wavelength.toPx()
        val centerY = size.height / 2f
        val activeWidth = if (indeterminate) size.width else size.width * animatedProgress

        // 1. Draw inactive baseline track
        drawLine(
            color = trackColor.copy(alpha = 0.6f),
            start = Offset(0f, centerY),
            end = Offset(size.width, centerY),
            strokeWidth = strokePx * 0.75f,
            cap = StrokeCap.Round
        )

        // 2. Draw active squiggly wave (clipped to active width)
        if (activeWidth > 0f) {
            val phasePx = if (active || indeterminate) -phaseOffset * waveLenPx else 0f
            clipRect(left = 0f, top = 0f, right = activeWidth, bottom = size.height) {
                val path = Path().apply {
                    val half = waveLenPx / 2f
                    val quarter = waveLenPx / 4f
                    var x = phasePx - waveLenPx
                    var up = true

                    moveTo(x, centerY)
                    while (x < size.width + waveLenPx * 2) {
                        val nextX = x + half
                        val peakY = if (up) centerY - ampPx else centerY + ampPx
                        val ctrlX = x + quarter
                        quadraticBezierTo(ctrlX, peakY, nextX, centerY)
                        x = nextX
                        up = !up
                    }
                }

                drawPath(
                    path = path,
                    color = color,
                    style = Stroke(
                        width = strokePx,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
        }
    }
}
