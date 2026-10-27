package dev.pulse.monitor.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import dev.pulse.monitor.ui.theme.PulseTheme
import kotlin.math.abs

@Immutable
data class ChartPoint(val x: Float, val y: Float)

@Immutable
data class ChartSeries(
    val name: String,
    val points: List<ChartPoint>,
    val color: Color,
    val fill: Boolean = true,
    /** Break the line when two consecutive points are further apart than this (x units). */
    val gapX: Float? = null,
    val dashed: Boolean = false,
)

/**
 * Lightweight Canvas line chart for live and historical data. Drag or press on it to scrub:
 * a crosshair shows the exact values at that moment.
 */
@Composable
fun LineChart(
    series: List<ChartSeries>,
    xMin: Float,
    xMax: Float,
    yMin: Float,
    yMax: Float,
    contentDescription: String,
    modifier: Modifier = Modifier,
    bands: List<ClosedFloatingPointRange<Float>> = emptyList(),
    bandColor: Color = PulseTheme.colors.battery.copy(alpha = 0.10f),
    yLabels: List<Pair<Float, String>> = emptyList(),
    xLabels: List<Pair<Float, String>> = emptyList(),
    scrubLabel: ((x: Float, values: List<Pair<ChartSeries, ChartPoint>>) -> String)? = null,
) {
    val colors = PulseTheme.colors
    val measurer = rememberTextMeasurer()
    val labelStyle = PulseTheme.type.caption.copy(color = colors.textTertiary)
    val tooltipStyle = PulseTheme.type.caption.copy(color = colors.textPrimary)
    var scrubX by remember { mutableStateOf<Float?>(null) }

    Canvas(
        modifier
            .semantics { this.contentDescription = contentDescription }
            .pointerInput(scrubLabel != null) {
                if (scrubLabel == null) return@pointerInput
                detectTapGestures(onPress = { offset ->
                    scrubX = offset.x
                    tryAwaitRelease()
                    scrubX = null
                })
            }
            .pointerInput(scrubLabel != null) {
                if (scrubLabel == null) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { scrubX = it.x },
                    onDragEnd = { scrubX = null },
                    onDragCancel = { scrubX = null },
                ) { change, _ ->
                    scrubX = change.position.x
                    change.consume()
                }
            },
    ) {
        val leftPad = if (yLabels.isEmpty()) 0f else 40.dp.toPx()
        val bottomPad = if (xLabels.isEmpty()) 0f else 20.dp.toPx()
        val topPad = 6.dp.toPx()
        val chartW = size.width - leftPad
        val chartH = size.height - bottomPad - topPad
        if (chartW <= 0f || chartH <= 0f) return@Canvas
        val xSpan = (xMax - xMin).takeIf { it > 0f } ?: 1f
        val ySpan = (yMax - yMin).takeIf { it > 0f } ?: 1f
        fun px(x: Float) = leftPad + (x - xMin) / xSpan * chartW
        fun py(y: Float) = topPad + chartH - (y - yMin) / ySpan * chartH

        // Grid + y labels.
        val gridColor = colors.outline.copy(alpha = 0.5f)
        yLabels.forEach { (value, text) ->
            val y = py(value)
            drawLine(
                gridColor, Offset(leftPad, y), Offset(size.width, y), strokeWidth = 1f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
            )
            val layout = measurer.measure(text, labelStyle)
            drawText(layout, topLeft = Offset(0f, y - layout.size.height / 2f))
        }
        xLabels.forEach { (value, text) ->
            val x = px(value)
            if (x < leftPad - 1f || x > size.width + 1f) return@forEach
            val layout = measurer.measure(text, labelStyle)
            val left = (x - layout.size.width / 2f).coerceIn(leftPad, size.width - layout.size.width)
            drawText(layout, topLeft = Offset(left, size.height - layout.size.height))
        }

        clipRect(left = leftPad, top = 0f, right = size.width, bottom = topPad + chartH) {
            // Shaded bands (e.g. "charging" periods).
            bands.forEach { band ->
                val l = px(band.start).coerceAtLeast(leftPad)
                val r = px(band.endInclusive).coerceAtMost(size.width)
                if (r > l) {
                    drawRect(bandColor, topLeft = Offset(l, topPad), size = Size(r - l, chartH))
                }
            }

            series.forEach { s ->
                val segments = splitSegments(s)
                segments.forEach { seg ->
                    if (seg.isEmpty()) return@forEach
                    val line = Path()
                    seg.forEachIndexed { i, p ->
                        val x = px(p.x)
                        val y = py(p.y)
                        if (i == 0) line.moveTo(x, y) else line.lineTo(x, y)
                    }
                    if (s.fill && seg.size > 1) {
                        val area = Path().apply {
                            addPath(line)
                            lineTo(px(seg.last().x), topPad + chartH)
                            lineTo(px(seg.first().x), topPad + chartH)
                            close()
                        }
                        drawPath(
                            area,
                            Brush.verticalGradient(
                                listOf(s.color.copy(alpha = 0.32f), s.color.copy(alpha = 0.0f)),
                                startY = topPad,
                                endY = topPad + chartH,
                            ),
                        )
                    }
                    if (seg.size == 1) {
                        drawCircle(s.color, radius = 2.5.dp.toPx(), center = Offset(px(seg[0].x), py(seg[0].y)))
                    } else {
                        drawPath(
                            line,
                            s.color,
                            style = Stroke(
                                width = 2.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round,
                                pathEffect = if (s.dashed) PathEffect.dashPathEffect(floatArrayOf(10f, 8f)) else null,
                            ),
                        )
                    }
                }
            }
        }

        // Scrub crosshair + tooltip.
        val sx = scrubX
        if (sx != null && scrubLabel != null) {
            val xValue = xMin + ((sx - leftPad) / chartW).coerceIn(0f, 1f) * xSpan
            val nearest = series.mapNotNull { s ->
                s.points.minByOrNull { abs(it.x - xValue) }?.let { s to it }
            }
            if (nearest.isNotEmpty()) {
                val cx = px(nearest.first().second.x)
                drawLine(colors.textSecondary, Offset(cx, topPad), Offset(cx, topPad + chartH), strokeWidth = 1.dp.toPx())
                nearest.forEach { (s, p) ->
                    drawCircle(colors.background, radius = 5.dp.toPx(), center = Offset(px(p.x), py(p.y)))
                    drawCircle(s.color, radius = 3.5.dp.toPx(), center = Offset(px(p.x), py(p.y)))
                }
                val layout = measurer.measure(scrubLabel(nearest.first().second.x, nearest), tooltipStyle)
                val pad = 8.dp.toPx()
                val boxW = layout.size.width + pad * 2
                val boxH = layout.size.height + pad * 1.5f
                val left = (cx - boxW / 2).coerceIn(leftPad, size.width - boxW)
                drawRoundRect(
                    color = colors.surface3,
                    topLeft = Offset(left, topPad),
                    size = Size(boxW, boxH),
                    cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
                )
                drawText(layout, topLeft = Offset(left + pad, topPad + pad * 0.75f))
            }
        }
    }
}

private fun splitSegments(s: ChartSeries): List<List<ChartPoint>> {
    if (s.points.isEmpty()) return emptyList()
    val gap = s.gapX ?: return listOf(s.points)
    val out = mutableListOf<MutableList<ChartPoint>>()
    var current = mutableListOf<ChartPoint>()
    var prev: ChartPoint? = null
    for (p in s.points) {
        if (prev != null && p.x - prev.x > gap) {
            out += current
            current = mutableListOf()
        }
        current += p
        prev = p
    }
    out += current
    return out
}

/** Rounds a maximum up to a "nice" chart ceiling (1, 2, 5 × 10ⁿ). */
fun niceCeiling(value: Float): Float {
    if (value <= 0f) return 1f
    var magnitude = 1f
    while (magnitude * 10f <= value) magnitude *= 10f
    while (magnitude > value) magnitude /= 10f
    for (m in floatArrayOf(1f, 2f, 5f, 10f)) {
        if (magnitude * m >= value) return magnitude * m
    }
    return magnitude * 10f
}
