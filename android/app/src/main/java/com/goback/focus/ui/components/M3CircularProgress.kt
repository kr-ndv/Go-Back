package com.goback.focus.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Material 3 Expressive Circular Squiggly Progress Indicator.
 * Renders an expressive wavy/squiggly path along a circular arc with
 * continuous phase flow and breathing animation when active.
 */
@Composable
fun M3CircularProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 230.dp,
    strokeWidth: Dp = 7.dp,
    amplitude: Dp = 4.dp,
    wavesCount: Int = 26,
    active: Boolean = false,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    content: @Composable () -> Unit = {}
) {
    val clamped = progress.coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = clamped,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "circular_squiggly_progress"
    )

    // Continuous wave phase ripple when running
    val infiniteTransition = rememberInfiniteTransition(label = "wave_phase_anim")
    val phaseOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (active) (2f * PI.toFloat()) else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )

    // Expressive breathing pulse when active
    val scalePulse by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (active) 1.018f else 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale_pulse"
    )

    Box(
        modifier = modifier
            .size(size)
            .scale(scalePulse),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val strokePx = strokeWidth.toPx()
            val ampPx = amplitude.toPx()
            val cx = this.size.width / 2f
            val cy = this.size.height / 2f
            val baseRadius = (minOf(this.size.width, this.size.height) - strokePx) / 2f - ampPx

            // 1. Draw Inactive Background Track Circle
            drawCircle(
                color = trackColor.copy(alpha = 0.5f),
                radius = baseRadius,
                center = androidx.compose.ui.geometry.Offset(cx, cy),
                style = Stroke(width = strokePx * 0.75f, cap = StrokeCap.Round)
            )

            // 2. Draw Active Squiggly Wave Arc
            if (animatedProgress > 0.005f) {
                val startAngleRad = -PI.toFloat() / 2f // Top (-90 degrees)
                val totalSweepRad = animatedProgress * (2f * PI.toFloat())
                val steps = (totalSweepRad * 120f).toInt().coerceAtLeast(12)
                val angleStep = totalSweepRad / steps.toFloat()

                val path = Path()
                for (i in 0..steps) {
                    val theta = startAngleRad + i * angleStep
                    val angleFromStart = i * angleStep
                    val waveOscillation = sin(wavesCount.toFloat() * angleFromStart + phaseOffset)
                    val r = baseRadius + ampPx * waveOscillation
                    val x = cx + r * cos(theta)
                    val y = cy + r * sin(theta)

                    if (i == 0) {
                        path.moveTo(x, y)
                    } else {
                        path.lineTo(x, y)
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

        // Center content (timer countdown, icon, status)
        content()
    }
}
