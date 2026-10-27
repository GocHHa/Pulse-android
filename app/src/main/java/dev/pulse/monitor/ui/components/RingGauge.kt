package dev.pulse.monitor.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

/**
 * A 270° instrument-cluster ring gauge. The arc sweeps in from zero the first time it
 * appears and then glides between values on a spring, like a real needle with a little mass.
 *
 * @param progress 0..1, or null to draw only the track (unavailable reading).
 */
@Composable
fun RingGauge(
    progress: Float?,
    color: Color,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 12.dp,
    sweepAngle: Float = 270f,
    ticks: Int = 40,
    glow: Boolean = true,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val colors = dev.pulse.monitor.ui.theme.PulseTheme.colors
    val target = (progress ?: 0f).coerceIn(0f, 1f)
    val animated = remember { Animatable(0f) }
    LaunchedEffect(target) {
        animated.animateTo(
            targetValue = target,
            animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessVeryLow),
        )
    }
    val tint by animateFloatAsState(if (progress == null) 0f else 1f, tween(400), label = "ringTint")
    val track = colors.track
    val tickColor = colors.textTertiary

    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            val inset = stroke * 1.6f
            val diameter = size.minDimension - inset * 2
            val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
            val arcSize = Size(diameter, diameter)
            val start = 90f + (360f - sweepAngle) / 2f

            // Tick marks around the outside, brighter where the gauge has reached.
            if (ticks > 0) {
                drawTicks(start, sweepAngle, ticks, diameter / 2 + stroke * 1.15f, stroke * 0.45f, tickColor, color, animated.value)
            }

            drawArc(
                color = track,
                startAngle = start,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )

            val sweep = sweepAngle * animated.value
            if (sweep > 0.5f && tint > 0f) {
                if (glow) {
                    // Cheap glow: the same arc drawn wider and fainter underneath.
                    drawArc(
                        color = color.copy(alpha = 0.10f * tint),
                        startAngle = start, sweepAngle = sweep, useCenter = false,
                        topLeft = topLeft, size = arcSize,
                        style = Stroke(width = stroke * 2.8f, cap = StrokeCap.Round),
                    )
                    drawArc(
                        color = color.copy(alpha = 0.16f * tint),
                        startAngle = start, sweepAngle = sweep, useCenter = false,
                        topLeft = topLeft, size = arcSize,
                        style = Stroke(width = stroke * 1.7f, cap = StrokeCap.Round),
                    )
                }
                drawArc(
                    color = color.copy(alpha = tint),
                    startAngle = start, sweepAngle = sweep, useCenter = false,
                    topLeft = topLeft, size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
                // Bright "needle tip" at the end of the arc.
                val angle = Math.toRadians((start + sweep).toDouble())
                val r = diameter / 2
                val center = Offset(size.width / 2, size.height / 2)
                val tip = Offset(center.x + r * cos(angle).toFloat(), center.y + r * sin(angle).toFloat())
                drawCircle(Color.White.copy(alpha = 0.9f * tint), radius = stroke * 0.32f, center = tip)
            }
        }
        content()
    }
}

private fun DrawScope.drawTicks(
    start: Float,
    sweep: Float,
    count: Int,
    radius: Float,
    length: Float,
    idle: Color,
    active: Color,
    progress: Float,
) {
    val center = Offset(size.width / 2, size.height / 2)
    for (i in 0..count) {
        val f = i / count.toFloat()
        val angle = Math.toRadians((start + sweep * f).toDouble())
        val major = i % 5 == 0
        val len = if (major) length * 1.8f else length
        val c = cos(angle).toFloat()
        val s = sin(angle).toFloat()
        val from = Offset(center.x + radius * c, center.y + radius * s)
        val to = Offset(center.x + (radius + len) * c, center.y + (radius + len) * s)
        val lit = f <= progress
        drawLine(
            color = if (lit) active.copy(alpha = if (major) 0.9f else 0.6f) else idle.copy(alpha = if (major) 0.7f else 0.35f),
            start = from,
            end = to,
            strokeWidth = if (major) 1.6.dp.toPx() else 1.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

/**
 * Text whose number glides to its new value instead of jumping (like an odometer).
 * Uses the tabular-digit style so the width doesn't wobble while it counts.
 */
@Composable
fun AnimatedNumber(
    value: Float?,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    decimals: Int = 0,
    prefix: String = "",
    suffix: String = "",
    placeholder: String = "—",
    format: ((Float) -> String)? = null,
) {
    val animated by animateFloatAsState(
        targetValue = value ?: 0f,
        animationSpec = tween(durationMillis = 650),
        label = "number",
    )
    val text = if (value == null) placeholder else {
        val core = format?.invoke(animated) ?: dev.pulse.monitor.util.Format.number(animated, decimals)
        "$prefix$core$suffix"
    }
    Text(text = text, style = style, color = color, modifier = modifier, maxLines = 1)
}

/**
 * Font size that grows with the user's font-scale setting but is capped, for numbers that
 * live inside fixed-size gauges (the gauges also expose full text to screen readers).
 */
@Composable
fun cappedSp(base: Float, maxScale: Float = 1.3f): TextUnit {
    val fontScale = LocalDensity.current.fontScale
    val effective = fontScale.coerceAtMost(maxScale)
    return (base * effective / fontScale).sp
}
