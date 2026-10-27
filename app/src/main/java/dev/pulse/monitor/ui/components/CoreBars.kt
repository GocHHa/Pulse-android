package dev.pulse.monitor.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.pulse.monitor.data.model.CoreInfo
import dev.pulse.monitor.ui.theme.PulseTheme
import dev.pulse.monitor.util.Format

/**
 * CPU cores as an audio-equalizer: one LED column per core, lit up to how close that core
 * is to its maximum clock. Bars bounce on a soft spring so they feel alive, not jumpy.
 */
@Composable
fun CoreBars(
    cores: List<CoreInfo>,
    color: Color,
    modifier: Modifier = Modifier,
    barHeight: Dp = 140.dp,
    showLabels: Boolean = true,
    segments: Int = 16,
) {
    val colors = PulseTheme.colors
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        cores.forEach { core ->
            val load = core.load
            val description = when {
                !core.online -> "Core ${core.index} offline"
                core.currentKhz == null -> "Core ${core.index} frequency not available"
                else -> "Core ${core.index} at ${Format.frequency(core.currentKhz)}" +
                    (core.maxKhz?.let { ", maximum ${Format.frequency(it)}" } ?: "")
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clearAndSetSemantics { contentDescription = description },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                EqualizerColumn(
                    level = if (core.online) load ?: 0f else 0f,
                    color = color,
                    track = colors.track,
                    segments = segments,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(barHeight),
                )
                if (showLabels) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = when {
                            !core.online -> "OFF"
                            core.currentKhz == null -> "—"
                            else -> shortFreq(core.currentKhz)
                        },
                        style = PulseTheme.type.smallNumber.copy(fontSize = cappedSp(11f)),
                        color = if (core.online) colors.textPrimary else colors.textTertiary,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                    Text(
                        text = "C${core.index}",
                        style = PulseTheme.type.caption.copy(fontSize = cappedSp(10f)),
                        color = colors.textTertiary,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun EqualizerColumn(
    level: Float,
    color: Color,
    track: Color,
    segments: Int,
    modifier: Modifier,
) {
    val animated by animateFloatAsState(
        targetValue = level.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow),
        label = "eq",
    )
    val hot = PulseTheme.colors.heat
    Canvas(modifier) {
        val gap = 2.dp.toPx()
        val segH = (size.height - gap * (segments - 1)) / segments
        val lit = animated * segments
        for (i in 0 until segments) {
            val y = size.height - (i + 1) * segH - i * gap
            val fraction = i / (segments - 1f)
            // Low segments in the category colour, top segments warm toward red, like a VU meter.
            val segColor = if (fraction < 0.7f) color else lerp(color, hot, (fraction - 0.7f) / 0.3f)
            val on = (lit - i).coerceIn(0f, 1f)
            drawRoundRect(
                color = track,
                topLeft = Offset(0f, y),
                size = Size(size.width, segH),
                cornerRadius = CornerRadius(segH / 3, segH / 3),
            )
            if (on > 0f) {
                drawRoundRect(
                    color = segColor.copy(alpha = 0.25f + 0.75f * on),
                    topLeft = Offset(0f, y),
                    size = Size(size.width, segH),
                    cornerRadius = CornerRadius(segH / 3, segH / 3),
                )
            }
        }
    }
}

private fun shortFreq(khz: Long): String =
    if (khz >= 1_000_000) Format.number(khz / 1_000_000f, 1) + "G" else "${khz / 1000}M"

/** Tiny one-line equalizer used on the dashboard card. */
@Composable
fun MiniCoreBars(cores: List<CoreInfo>, color: Color, modifier: Modifier = Modifier) {
    CoreBars(
        cores = cores,
        color = color,
        modifier = modifier,
        barHeight = 46.dp,
        showLabels = false,
        segments = 8,
    )
}
