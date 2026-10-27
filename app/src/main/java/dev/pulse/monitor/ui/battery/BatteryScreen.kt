package dev.pulse.monitor.ui.battery

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.pulse.monitor.R
import dev.pulse.monitor.data.battery.DrainTracker
import dev.pulse.monitor.data.model.BatteryHealth
import dev.pulse.monitor.data.model.BatterySnapshot
import dev.pulse.monitor.data.settings.AppSettings
import dev.pulse.monitor.data.settings.TempUnit
import dev.pulse.monitor.ui.BatteryViewModel
import dev.pulse.monitor.ui.Labels
import dev.pulse.monitor.ui.LocalAppContainer
import dev.pulse.monitor.ui.components.BatteryHeroGauge
import dev.pulse.monitor.ui.components.ChartPoint
import dev.pulse.monitor.ui.components.ChartSeries
import dev.pulse.monitor.ui.components.Divider
import dev.pulse.monitor.ui.components.InstrumentCard
import dev.pulse.monitor.ui.components.LineChart
import dev.pulse.monitor.ui.components.LinearMeter
import dev.pulse.monitor.ui.components.NotAvailable
import dev.pulse.monitor.ui.components.Pill
import dev.pulse.monitor.ui.components.ScreenColumn
import dev.pulse.monitor.ui.components.ScreenHeader
import dev.pulse.monitor.ui.components.SectionLabel
import dev.pulse.monitor.ui.components.StatRow
import dev.pulse.monitor.ui.components.niceCeiling
import dev.pulse.monitor.ui.theme.PulseTheme
import dev.pulse.monitor.ui.theme.heatTint
import dev.pulse.monitor.util.Format
import kotlin.math.abs
import kotlin.math.max

@Composable
fun BatteryScreen(padding: PaddingValues, onSettings: () -> Unit) {
    val container = LocalAppContainer.current
    val vm: BatteryViewModel = viewModel { BatteryViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()
    val power by vm.powerHistory.collectAsStateWithLifecycle()
    val b = state.battery
    val settings = state.settings
    val fahrenheit = settings.tempUnit == TempUnit.FAHRENHEIT
    val colors = PulseTheme.colors
    val accent = colors.heatTint(colors.battery, b?.temperatureC, settings.heatThresholdC)

    ScreenColumn(padding) {
        item {
            ScreenHeader(title = "Battery", eyebrow = "Power system", accent = colors.battery, onSettings = onSettings)
        }
        item {
            InstrumentCard(Modifier.fillMaxWidth(), accent = accent) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    BatteryHeroGauge(
                        level = b?.levelPercent,
                        charging = b?.isCharging == true,
                        watts = b?.powerW,
                        accent = accent,
                        statusText = b?.let { Labels.status(it) } ?: "Reading…",
                        detailText = b?.powerW?.let { Format.signedWatts(it) },
                        accessibilityText = Labels.batteryA11y(b, fahrenheit),
                        size = 260.dp,
                    )
                }
            }
        }
        item { ChargingCard(b) }
        item { PowerTraceCard(power) }
        item { HealthCard(b, settings, fahrenheit) }
        item { DrainCard(b) }
        item { SensorCard(b) }
    }
}

@Composable
private fun ChargingCard(b: BatterySnapshot?) {
    val colors = PulseTheme.colors
    InstrumentCard(Modifier.fillMaxWidth(), accent = colors.battery) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Charging", accent = colors.battery, modifier = Modifier.weight(1f))
            if (b != null) {
                Pill(
                    text = if (b.isPlugged) Labels.plug(b.plug) else "Unplugged",
                    color = if (b.isPlugged) colors.battery else colors.textSecondary,
                    icon = R.drawable.ic_plug,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        StatRow("Status", b?.let { Labels.status(it) }, icon = R.drawable.ic_nav_battery)
        Divider()
        StatRow(
            label = "Power",
            value = b?.powerW?.let { Format.signedWatts(it) },
            supporting = "Into (+) or out of (−) the battery",
            icon = R.drawable.ic_bolt,
            valueColor = if ((b?.powerW ?: 0f) > 0f) colors.battery else colors.textPrimary,
        )
        Divider()
        StatRow("Current", b?.currentMa?.let { Format.signedMilliamps(it) }, icon = R.drawable.ic_bolt)
        Divider()
        StatRow("Voltage", b?.voltageMv?.let { Format.volts(it) }, icon = R.drawable.ic_bolt)
        Divider()
        when {
            b == null -> StatRow("Time", "—")
            b.isCharging -> StatRow(
                label = "Time until full",
                value = b.chargeTimeRemainingMs?.let { Format.duration(it) },
                supporting = "Android's own estimate",
                icon = R.drawable.ic_nav_history,
            )
            !b.isPlugged -> StatRow(
                label = "Time left on battery",
                value = b.timeLeftOnBatteryMs?.let { Format.duration(it) } ?: "Estimating…",
                supporting = "At your recent drain rate",
                icon = R.drawable.ic_nav_history,
            )
            else -> StatRow("Time until full", "Not charging right now", icon = R.drawable.ic_nav_history)
        }
    }
}

@Composable
private fun PowerTraceCard(samples: List<Float?>) {
    val colors = PulseTheme.colors
    InstrumentCard(Modifier.fillMaxWidth(), accent = colors.battery) {
        SectionLabel("Live power · 90 s", accent = colors.battery)
        Spacer(Modifier.height(12.dp))
        val values = samples.filterNotNull()
        if (samples.isNotEmpty() && values.isEmpty()) {
            NotAvailable(reason = "This device doesn't report charging current")
        } else {
            val peak = niceCeiling(max(1f, values.maxOfOrNull { abs(it) } ?: 1f))
            val hasNegative = values.any { it < 0f }
            val yMin = if (hasNegative) -peak else 0f
            LineChart(
                series = listOf(
                    ChartSeries(
                        name = "Power",
                        points = samples.mapIndexedNotNull { i, v -> v?.let { ChartPoint(i.toFloat(), it) } },
                        color = colors.battery,
                    ),
                ),
                xMin = 0f,
                xMax = 89f,
                yMin = yMin,
                yMax = peak,
                yLabels = listOfNotNull(
                    peak to "${Format.number(peak, 0)} W",
                    0f to "0 W",
                    if (hasNegative) -peak to "−${Format.number(peak, 0)} W" else null,
                ),
                contentDescription = "Battery power over the last 90 seconds",
                scrubLabel = { _, picked -> picked.joinToString { Format.signedWatts(it.second.y) } },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Drag across the chart to read exact values. Positive means energy is going into the battery.",
                style = PulseTheme.type.caption,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun HealthCard(b: BatterySnapshot?, settings: AppSettings, fahrenheit: Boolean) {
    val colors = PulseTheme.colors
    val temp = b?.temperatureC
    val heat = colors.heatTint(colors.battery, temp, settings.heatThresholdC)
    InstrumentCard(Modifier.fillMaxWidth(), accent = heat) {
        SectionLabel("Health & temperature", accent = heat)
        Spacer(Modifier.height(4.dp))
        StatRow(
            label = "Health",
            value = b?.health?.takeIf { it != BatteryHealth.UNKNOWN }?.let { Labels.health(it) },
            icon = R.drawable.ic_heart,
            valueColor = if (b?.health == BatteryHealth.GOOD) colors.battery else colors.warning,
        )
        Divider()
        StatRow(
            label = "Temperature",
            value = temp?.let { "${Format.temp(it, fahrenheit)} · ${Labels.tempFeel(it, settings.heatThresholdC)}" },
            icon = R.drawable.ic_thermo,
            valueColor = heat,
        )
        if (temp != null) {
            // Bar spans 20–50 °C so typical use sits in the middle.
            LinearMeter(fraction = ((temp - 20f) / 30f).coerceIn(0f, 1f), color = heat)
            Spacer(Modifier.height(6.dp))
        }
        Divider()
        StatRow(
            label = "Charge cycles",
            value = b?.cycleCount?.toString(),
            supporting = when {
                b == null -> null
                !b.cycleCountApiSupported -> "Needs Android 14 or newer"
                b.cycleCount == null -> "This device doesn't report it"
                else -> "Full charge-equivalents so far"
            },
            icon = R.drawable.ic_nav_history,
        )
        Divider()
        StatRow("Technology", b?.technology, icon = R.drawable.ic_nav_battery)
        Spacer(Modifier.height(8.dp))
        Text(
            "Exact remaining capacity (\"battery health %\") is only readable by system apps on Android, " +
                "so Pulse doesn't guess it. Samsung shows it in Settings › Battery › Battery information.",
            style = PulseTheme.type.caption,
            color = colors.textTertiary,
        )
    }
}

@Composable
private fun DrainCard(b: BatterySnapshot?) {
    val colors = PulseTheme.colors
    InstrumentCard(Modifier.fillMaxWidth(), accent = colors.battery) {
        SectionLabel("Drain rate", accent = colors.battery)
        Spacer(Modifier.height(4.dp))
        val drain = b?.drain
        StatRow(
            label = "Average drain",
            value = drain?.let { Labels.drain(it) },
            supporting = when (drain) {
                is DrainTracker.Estimate.Rate -> "Measured over ${Format.duration(drain.spanMs)}"
                is DrainTracker.Estimate.Below -> "No 1% drop in ${Format.duration(drain.spanMs)}"
                is DrainTracker.Estimate.Measuring -> "Needs a 1% drop to measure"
                DrainTracker.Estimate.NotDischarging -> "Plugged in"
                null -> null
            },
            icon = R.drawable.ic_nav_history,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Like a car's average-consumption display: Pulse compares your battery level now with where it " +
                "was when you unplugged (up to 3 hours back) and divides by the time in between.",
            style = PulseTheme.type.caption,
            color = colors.textTertiary,
        )
    }
}

@Composable
private fun SensorCard(b: BatterySnapshot?) {
    val colors = PulseTheme.colors
    InstrumentCard(Modifier.fillMaxWidth(), accent = colors.textTertiary) {
        SectionLabel("Current sensor")
        Spacer(Modifier.height(4.dp))
        StatRow("Reported unit", b?.currentUnit?.let { Labels.sensorUnit(it) })
        Divider()
        StatRow("Sign convention", b?.let { Labels.signMode(it.signMode) })
        Spacer(Modifier.height(4.dp))
        Text(
            "Samsung phones report charging current in different units and signs. Pulse detects this " +
                "automatically: a phone can't draw 20 A, so huge numbers must be microamps, and unplugged " +
                "readings must be draining, which reveals the sign.",
            style = PulseTheme.type.caption,
            color = colors.textTertiary,
        )
    }
}
