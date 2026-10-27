package dev.pulse.monitor.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.pulse.monitor.ui.theme.PulseTheme
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * The hero battery gauge: a 270° ring around a disc of "liquid" whose height is the charge
 * level. While charging, the liquid's waves move and glowing rings pulse outward — both get
 * faster the more watts are flowing in, like a tachometer for your charger.
 */
@Composable
fun BatteryHeroGauge(
    level: Int?,
    charging: Boolean,
    watts: Float?,
    accent: Color,
    statusText: String,
    detailText: String?,
    accessibilityText: String,
    modifier: Modifier = Modifier,
    size: Dp = 300.dp,
) {
    val colors = PulseTheme.colors
    val fillTarget = (level ?: 0) / 100f
    val fill by animateFloatAsState(
        targetValue = fillTarget,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessVeryLow),
        label = "liquid",
    )
    // Wave speed in radians/second: calm idle drift, up to ~4x faster at 45 W+.
    val power = abs(watts ?: 0f)
    val speedTarget = if (charging) 1.6f + (power.coerceAtMost(45f) / 45f) * 5.4f else 0.6f
    val speed by animateFloatAsState(speedTarget, tween(1200), label = "waveSpeed")
    val pulseAlpha by animateFloatAsState(if (charging) 1f else 0f, tween(600), label = "pulseAlpha")

    var phase by remember { mutableFloatStateOf(0f) }
    var pulse by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                val dt = ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
                last = now
                phase = (phase + dt * speed) % (2f * PI.toFloat() * 100f)
                // One pulse ring per ~1.6 s at low power, ~0.6 s at high power.
                pulse = (pulse + dt * (speed / 4.5f)) % 1f
            }
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .clearAndSetSemantics { contentDescription = accessibilityText },
        contentAlignment = Alignment.Center,
    ) {
        RingGauge(
            progress = level?.let { it / 100f },
            color = accent,
            strokeWidth = 14.dp,
            ticks = 50,
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
        )

        // Liquid disc + charging pulses.
        Canvas(
            Modifier
                .fillMaxSize()
                .padding(size * 0.17f)
                .clipToBounds(),
        ) {
            val radius = this.size.minDimension / 2
            val center = Offset(this.size.width / 2, this.size.height / 2)

            // Pulse rings expanding from the disc edge.
            if (pulseAlpha > 0.01f) {
                for (k in 0..1) {
                    val p = (pulse + k * 0.5f) % 1f
                    drawCircle(
                        color = accent.copy(alpha = (1f - p) * 0.35f * pulseAlpha),
                        radius = radius * (0.92f + p * 0.08f),
                        center = center,
                        style = Stroke(width = 2.dp.toPx() * (1f - p) + 0.5f),
                    )
                }
            }

            val disc = Path().apply {
                addOval(androidx.compose.ui.geometry.Rect(center, radius * 0.9f))
            }
            drawCircle(color = colors.surface3.copy(alpha = 0.6f), radius = radius * 0.9f, center = center)
            clipPath(disc) {
                val discTop = center.y - radius * 0.9f
                val discHeight = radius * 1.8f
                val surfaceY = discTop + discHeight * (1f - fill)
                val amplitude = radius * if (charging) 0.045f else 0.025f
                if (fill > 0f) {
                    drawWave(surfaceY, amplitude, phase * 0.8f + 1.3f, 1.3f, accent.copy(alpha = 0.28f))
                    drawWave(surfaceY, amplitude, phase, 1.0f, accent.copy(alpha = 0.55f), Brush.verticalGradient(
                        colors = listOf(accent.copy(alpha = 0.55f), accent.copy(alpha = 0.18f)),
                        startY = surfaceY,
                        endY = discTop + discHeight,
                    ))
                }
            }
            // Glass rim.
            drawCircle(
                color = accent.copy(alpha = 0.25f),
                radius = radius * 0.9f,
                center = center,
                style = Stroke(width = 1.dp.toPx()),
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedNumber(
                value = level?.toFloat(),
                style = PulseTheme.type.hero.copy(fontSize = cappedSp(64f)),
                color = colors.textPrimary,
                suffix = "%",
                placeholder = "—",
            )
            Text(
                text = statusText.uppercase(),
                style = PulseTheme.type.sectionLabel,
                color = accent,
                textAlign = TextAlign.Center,
            )
            if (detailText != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = detailText,
                    style = PulseTheme.type.smallNumber.copy(fontSize = cappedSp(15f)),
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawWave(
    surfaceY: Float,
    amplitude: Float,
    phase: Float,
    frequency: Float,
    color: Color,
    brush: Brush? = null,
) {
    val w = size.width
    val path = Path().apply {
        moveTo(0f, size.height)
        lineTo(0f, surfaceY)
        val steps = 48
        for (i in 0..steps) {
            val x = w * i / steps
            val y = surfaceY + amplitude * sin((x / w) * 2f * PI.toFloat() * frequency + phase)
            lineTo(x, y)
        }
        lineTo(w, size.height)
        close()
    }
    if (brush != null) drawPath(path, brush) else drawPath(path, color)
}
