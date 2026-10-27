package dev.pulse.monitor.ui.history

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.pulse.monitor.R
import dev.pulse.monitor.data.history.HistoryEntity
import dev.pulse.monitor.data.settings.TempUnit
import dev.pulse.monitor.ui.HOUR_MS
import dev.pulse.monitor.ui.HistoryRange
import dev.pulse.monitor.ui.HistoryViewModel
import dev.pulse.monitor.ui.LocalAppContainer
import dev.pulse.monitor.ui.components.ChartPoint
import dev.pulse.monitor.ui.components.ChartSeries
import dev.pulse.monitor.ui.components.Divider
import dev.pulse.monitor.ui.components.InstrumentCard
import dev.pulse.monitor.ui.components.LineChart
import dev.pulse.monitor.ui.components.Pill
import dev.pulse.monitor.ui.components.ScreenColumn
import dev.pulse.monitor.ui.components.ScreenHeader
import dev.pulse.monitor.ui.components.SectionLabel
import dev.pulse.monitor.ui.components.SegmentedControl
import dev.pulse.monitor.ui.components.StatRow
import dev.pulse.monitor.ui.dashboard.chargingBands
import dev.pulse.monitor.ui.theme.PulseTheme
import dev.pulse.monitor.util.Format
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor

@Composable
fun HistoryScreen(padding: PaddingValues, onSettings: () -> Unit) {
    val container = LocalAppContainer.current
    val vm: HistoryViewModel = viewModel { HistoryViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = PulseTheme.colors
    val fahrenheit = state.settings.tempUnit == TempUnit.FAHRENHEIT
    val entries = state.entries
    val start = state.rangeStartMs
    val spanHours = state.range.durationMs / HOUR_MS.toFloat()
    val xLabels = remember(start, state.range) { timeLabels(start, state.range) }
    val gap = 0.75f // hours: a missing logging slot or two breaks the line instead of bridging it

    ScreenColumn(padding) {
        item {
            ScreenHeader(title = "History", eyebrow = "Logged every 15 min", accent = colors.battery, onSettings = onSettings)
        }
        item {
            SegmentedControl(
                options = HistoryRange.entries.map { it.label },
                selectedIndex = state.range.ordinal,
                onSelect = { vm.setRange(HistoryRange.entries[it]) },
                accent = colors.battery,
            )
        }
        if (state.loaded && entries.size < 2) {
            item {
                InstrumentCard(Modifier.fillMaxWidth(), accent = colors.battery) {
                    SectionLabel("Collecting data", accent = colors.battery)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Pulse saves a reading every 15 minutes in the background. Charts appear after the second " +
                            "reading and fill in over the day. ${entries.size} reading(s) so far in this range.",
                        style = PulseTheme.type.body,
                        color = colors.textSecondary,
                    )
                }
            }
        } else {
            item {
                InstrumentCard(Modifier.fillMaxWidth(), accent = colors.battery) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionLabel("Battery level", accent = colors.battery, modifier = Modifier.weight(1f))
                        Pill("Shaded = charging", colors.battery, icon = R.drawable.ic_bolt)
                    }
                    Spacer(Modifier.height(12.dp))
                    LineChart(
                        series = listOf(
                            ChartSeries(
                                name = "Battery",
                                points = entries.map { ChartPoint(hoursSince(start, it.timestamp), it.level.toFloat()) },
                                color = colors.battery,
                                gapX = gap,
                            ),
                        ),
                        xMin = 0f,
                        xMax = spanHours,
                        yMin = 0f,
                        yMax = 100f,
                        bands = chargingBands(entries, start),
                        bandColor = colors.battery.copy(alpha = 0.12f),
                        yLabels = listOf(100f to "100%", 50f to "50%", 0f to "0%"),
                        xLabels = xLabels,
                        contentDescription = "Battery level chart for the last ${state.range.label}",
                        scrubLabel = { x, picked ->
                            "${clock(start, x, state.range)} · ${picked.firstOrNull()?.second?.y?.toInt() ?: 0}%"
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(190.dp),
                    )
                }
            }
            item {
                val temps = entries.mapNotNull { e -> e.temperatureC?.let { ChartPoint(hoursSince(start, e.timestamp), Format.tempValue(it, fahrenheit)) } }
                InstrumentCard(Modifier.fillMaxWidth(), accent = colors.heat) {
                    SectionLabel("Battery temperature", accent = colors.heat)
                    Spacer(Modifier.height(12.dp))
                    if (temps.isEmpty()) {
                        dev.pulse.monitor.ui.components.NotAvailable()
                    } else {
                        val lo = floor((temps.minOf { it.y } - 2f) / 5f) * 5f
                        val hi = ceil((temps.maxOf { it.y } + 2f) / 5f) * 5f
                        val unit = Format.tempUnit(fahrenheit)
                        LineChart(
                            series = listOf(ChartSeries("Temperature", temps, colors.heat, gapX = gap)),
                            xMin = 0f,
                            xMax = spanHours,
                            yMin = lo,
                            yMax = hi,
                            yLabels = listOf(hi to "${hi.toInt()}$unit", ((lo + hi) / 2) to "${((lo + hi) / 2).toInt()}$unit", lo to "${lo.toInt()}$unit"),
                            xLabels = xLabels,
                            contentDescription = "Battery temperature chart for the last ${state.range.label}",
                            scrubLabel = { x, picked ->
                                "${clock(start, x, state.range)} · ${Format.number(picked.firstOrNull()?.second?.y ?: 0f, 1)}$unit"
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(170.dp),
                        )
                    }
                }
            }
            item { StatsCard(entries, fahrenheit) }
        }
        item {
            Text(
                "Readings older than 7 days are deleted automatically. Tip: press and drag on a chart to read exact values.",
                style = PulseTheme.type.caption,
                color = colors.textTertiary,
            )
        }
    }
}

@Composable
private fun StatsCard(entries: List<HistoryEntity>, fahrenheit: Boolean) {
    val colors = PulseTheme.colors
    val temps = entries.mapNotNull { it.temperatureC }
    InstrumentCard(Modifier.fillMaxWidth(), accent = colors.battery) {
        SectionLabel("Summary", accent = colors.battery)
        Spacer(Modifier.height(4.dp))
        StatRow("Readings", entries.size.toString(), icon = R.drawable.ic_nav_history)
        Divider()
        StatRow("Average temperature", if (temps.isEmpty()) null else Format.temp(temps.average().toFloat(), fahrenheit), icon = R.drawable.ic_thermo)
        Divider()
        StatRow("Peak temperature", temps.maxOrNull()?.let { Format.temp(it, fahrenheit) }, icon = R.drawable.ic_thermo)
        Divider()
        StatRow("Lowest level", entries.minOfOrNull { it.level }?.let { "$it%" }, icon = R.drawable.ic_nav_battery)
        Divider()
        StatRow(
            "Time charging",
            Format.duration(chargingMs(entries)),
            supporting = "Approximate, from 15-minute readings",
            icon = R.drawable.ic_bolt,
        )
    }
}

/** Sum of the intervals that began with a charging reading (gaps over 30 min are capped). */
private fun chargingMs(entries: List<HistoryEntity>): Long {
    var total = 0L
    for (i in 0 until entries.size - 1) {
        if (entries[i].charging) {
            total += (entries[i + 1].timestamp - entries[i].timestamp).coerceAtMost(30 * 60_000L)
        }
    }
    return total
}

private fun hoursSince(start: Long, t: Long): Float = (t - start) / HOUR_MS.toFloat()

private fun clock(start: Long, hours: Float, range: HistoryRange): String {
    val time = Date(start + (hours * HOUR_MS).toLong())
    val pattern = if (range == HistoryRange.DAY) "HH:mm" else "EEE HH:mm"
    return SimpleDateFormat(pattern, Locale.getDefault()).format(time)
}

/** X-axis labels: every 6 hours for the day view, one per day for the week view. */
private fun timeLabels(start: Long, range: HistoryRange): List<Pair<Float, String>> {
    val cal = Calendar.getInstance().apply { timeInMillis = start }
    val labels = mutableListOf<Pair<Float, String>>()
    if (range == HistoryRange.DAY) {
        cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        while (cal.get(Calendar.HOUR_OF_DAY) % 6 != 0 || cal.timeInMillis < start) cal.add(Calendar.HOUR_OF_DAY, 1)
        val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        while (cal.timeInMillis <= start + range.durationMs) {
            labels += hoursSince(start, cal.timeInMillis) to fmt.format(cal.time)
            cal.add(Calendar.HOUR_OF_DAY, 6)
        }
    } else {
        cal.set(Calendar.HOUR_OF_DAY, 12); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        if (cal.timeInMillis < start) cal.add(Calendar.DAY_OF_MONTH, 1)
        val fmt = SimpleDateFormat("EEE", Locale.getDefault())
        while (cal.timeInMillis <= start + range.durationMs) {
            labels += hoursSince(start, cal.timeInMillis) to fmt.format(cal.time)
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
    }
    return labels
}
