package dev.pulse.monitor.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.pulse.monitor.R
import dev.pulse.monitor.data.history.HistoryEntity
import dev.pulse.monitor.data.model.BatterySnapshot
import dev.pulse.monitor.data.model.CpuSnapshot
import dev.pulse.monitor.data.model.MemorySnapshot
import dev.pulse.monitor.data.model.NetworkSnapshot
import dev.pulse.monitor.data.model.ThermalLevel
import dev.pulse.monitor.data.settings.AppSettings
import dev.pulse.monitor.data.settings.TempUnit
import dev.pulse.monitor.ui.DashboardViewModel
import dev.pulse.monitor.ui.HOUR_MS
import dev.pulse.monitor.ui.Labels
import dev.pulse.monitor.ui.LocalAppContainer
import dev.pulse.monitor.ui.Routes
import dev.pulse.monitor.ui.components.AnimatedNumber
import dev.pulse.monitor.ui.components.BatteryHeroGauge
import dev.pulse.monitor.ui.components.ChartPoint
import dev.pulse.monitor.ui.components.ChartSeries
import dev.pulse.monitor.ui.components.InstrumentCard
import dev.pulse.monitor.ui.components.LineChart
import dev.pulse.monitor.ui.components.LinearMeter
import dev.pulse.monitor.ui.components.MiniCoreBars
import dev.pulse.monitor.ui.components.NotAvailable
import dev.pulse.monitor.ui.components.Pill
import dev.pulse.monitor.ui.components.PulseIcon
import dev.pulse.monitor.ui.components.RingGauge
import dev.pulse.monitor.ui.components.ScreenColumn
import dev.pulse.monitor.ui.components.ScreenHeader
import dev.pulse.monitor.ui.components.SectionLabel
import dev.pulse.monitor.ui.components.cappedSp
import dev.pulse.monitor.ui.components.niceCeiling
import dev.pulse.monitor.ui.theme.PulseTheme
import dev.pulse.monitor.ui.theme.heatTint
import dev.pulse.monitor.util.Format

@Composable
fun DashboardScreen(
    padding: PaddingValues,
    onOpen: (String) -> Unit,
    onOpenDetail: (String) -> Unit,
) {
    val container = LocalAppContainer.current
    val vm: DashboardViewModel = viewModel { DashboardViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()
    val downloads by vm.downloadHistory.collectAsStateWithLifecycle()
    val history by vm.historyPreview.collectAsStateWithLifecycle()
    val fahrenheit = state.settings.tempUnit == TempUnit.FAHRENHEIT

    ScreenColumn(padding) {
        item {
            ScreenHeader(
                title = "Pulse",
                eyebrow = "Live telemetry",
                accent = PulseTheme.colors.battery,
                onSettings = { onOpenDetail(Routes.SETTINGS) },
            )
        }
        item {
            HeroCard(state.battery, state.settings, fahrenheit) { onOpen(Routes.BATTERY) }
        }
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CpuCard(state.cpu, Modifier.weight(1f).fillMaxHeight()) { onOpen(Routes.PERFORMANCE) }
                MemoryCard(state.memory, Modifier.weight(1f).fillMaxHeight()) { onOpenDetail(Routes.MEMORY) }
            }
        }
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StorageCard(state.memory, Modifier.weight(1f).fillMaxHeight()) { onOpenDetail(Routes.MEMORY) }
                NetworkCard(state.network, downloads, Modifier.weight(1f).fillMaxHeight()) { onOpen(Routes.NETWORK) }
            }
        }
        item {
            ThermalCard(state.cpu, state.battery?.temperatureC, state.settings, fahrenheit) { onOpen(Routes.PERFORMANCE) }
        }
        item {
            HistoryTeaser(history) { onOpen(Routes.HISTORY) }
        }
    }
}

@Composable
private fun HeroCard(battery: BatterySnapshot?, settings: AppSettings, fahrenheit: Boolean, onClick: () -> Unit) {
    val colors = PulseTheme.colors
    val accent = colors.heatTint(colors.battery, battery?.temperatureC, settings.heatThresholdC)
    InstrumentCard(
        modifier = Modifier.fillMaxWidth(),
        accent = accent,
        onClick = onClick,
        onClickLabel = "Open battery details",
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            BatteryHeroGauge(
                level = battery?.levelPercent,
                charging = battery?.isCharging == true,
                watts = battery?.powerW,
                accent = accent,
                statusText = battery?.let { Labels.status(it) } ?: "Reading…",
                detailText = battery?.powerW?.let { Format.signedWatts(it) },
                accessibilityText = Labels.batteryA11y(battery, fahrenheit),
                size = 290.dp,
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val temp = battery?.temperatureC
            HeroStat(
                label = "Temp",
                value = temp?.let { Format.tempValue(it, fahrenheit) },
                decimals = 1,
                unit = Format.tempUnit(fahrenheit),
                color = colors.heatTint(colors.textPrimary, temp, settings.heatThresholdC),
                icon = R.drawable.ic_thermo,
                extra = Labels.tempFeel(temp, settings.heatThresholdC),
            )
            HeroStat(
                label = "Voltage",
                value = battery?.voltageMv?.let { it / 1000f },
                decimals = 2,
                unit = "V",
                color = colors.textPrimary,
                icon = R.drawable.ic_bolt,
            )
            val (label, text) = battery?.let { Labels.timeReadout(it) } ?: ("Time" to null)
            HeroTextStat(label = label, text = text, icon = R.drawable.ic_nav_history)
        }
    }
}

@Composable
private fun HeroStat(
    label: String,
    value: Float?,
    decimals: Int,
    unit: String,
    color: Color,
    icon: Int,
    extra: String? = null,
) {
    val colors = PulseTheme.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PulseIcon(icon, contentDescription = null, tint = colors.textTertiary, size = 14.dp)
            Spacer(Modifier.width(4.dp))
            Text(label.uppercase(), style = PulseTheme.type.sectionLabel, color = colors.textSecondary)
        }
        if (value == null) {
            Text("n/a", style = PulseTheme.type.number, color = colors.textTertiary)
            Text("Not available", style = PulseTheme.type.caption, color = colors.textTertiary)
        } else {
            AnimatedNumber(value = value, style = PulseTheme.type.number, color = color, decimals = decimals, suffix = unit)
            if (extra != null) Text(extra, style = PulseTheme.type.caption, color = colors.textSecondary)
        }
    }
}

@Composable
private fun HeroTextStat(label: String, text: String?, icon: Int) {
    val colors = PulseTheme.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PulseIcon(icon, contentDescription = null, tint = colors.textTertiary, size = 14.dp)
            Spacer(Modifier.width(4.dp))
            Text(label.uppercase(), style = PulseTheme.type.sectionLabel, color = colors.textSecondary)
        }
        Text(text ?: "—", style = PulseTheme.type.number, color = if (text == null) colors.textTertiary else colors.textPrimary)
        if (text == null) Text("Estimating", style = PulseTheme.type.caption, color = colors.textTertiary)
    }
}

@Composable
private fun CpuCard(cpu: CpuSnapshot?, modifier: Modifier, onClick: () -> Unit) {
    val colors = PulseTheme.colors
    InstrumentCard(modifier = modifier, accent = colors.cpu, onClick = onClick, onClickLabel = "Open CPU and thermal details") {
        SectionLabel("CPU", accent = colors.cpu)
        Spacer(Modifier.height(10.dp))
        if (cpu == null) {
            Text("Reading…", style = PulseTheme.type.caption, color = colors.textTertiary)
        } else if (!cpu.frequencyAvailable) {
            NotAvailable(compact = true, reason = "Clock speeds are hidden by this device")
            Spacer(Modifier.height(8.dp))
            Text("${cpu.cores.size} cores", style = PulseTheme.type.bodyStrong, color = colors.textPrimary)
        } else {
            MiniCoreBars(cpu.cores, colors.cpu)
            Spacer(Modifier.height(10.dp))
            val peak = cpu.cores.mapNotNull { it.currentKhz }.maxOrNull()
            Text(Format.frequency(peak), style = PulseTheme.type.number, color = colors.textPrimary)
            Text("fastest core now", style = PulseTheme.type.caption, color = colors.textSecondary)
        }
    }
}

@Composable
private fun SmallRingCard(
    label: String,
    accent: Color,
    fraction: Float?,
    line1: String,
    line2: String,
    modifier: Modifier,
    a11y: String,
    onClick: () -> Unit,
) {
    val colors = PulseTheme.colors
    InstrumentCard(modifier = modifier, accent = accent, onClick = onClick, onClickLabel = "Open memory and storage details") {
        SectionLabel(label, accent = accent)
        Spacer(Modifier.height(8.dp))
        if (fraction == null) {
            NotAvailable(compact = true)
        } else {
            Box(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = a11y }, contentAlignment = Alignment.Center) {
                RingGauge(
                    progress = fraction,
                    color = accent,
                    strokeWidth = 8.dp,
                    ticks = 0,
                    modifier = Modifier.size(104.dp),
                ) {
                    AnimatedNumber(
                        value = fraction * 100f,
                        style = PulseTheme.type.number.copy(fontSize = cappedSp(20f)),
                        color = colors.textPrimary,
                        suffix = "%",
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(line1, style = PulseTheme.type.bodyStrong, color = colors.textPrimary)
            Text(line2, style = PulseTheme.type.caption, color = colors.textSecondary)
        }
    }
}

@Composable
private fun MemoryCard(memory: MemorySnapshot?, modifier: Modifier, onClick: () -> Unit) {
    val colors = PulseTheme.colors
    val fraction = memory?.ramUsedFraction
    SmallRingCard(
        label = "RAM",
        accent = colors.ram,
        fraction = fraction,
        line1 = "${Format.bytesBinary(memory?.usedRamBytes)} used",
        line2 = "of ${Format.bytesBinary(memory?.totalRamBytes)}",
        modifier = modifier,
        a11y = "RAM ${fraction?.let { (it * 100).toInt() } ?: 0} percent used",
        onClick = onClick,
    )
}

@Composable
private fun StorageCard(memory: MemorySnapshot?, modifier: Modifier, onClick: () -> Unit) {
    val colors = PulseTheme.colors
    val fraction = memory?.storageUsedFraction
    SmallRingCard(
        label = "Storage",
        accent = colors.storage,
        fraction = fraction,
        line1 = "${Format.bytesDecimal(memory?.storageAvailableBytes)} free",
        line2 = "of ${Format.bytesDecimal(memory?.storageTotalBytes)}",
        modifier = modifier,
        a11y = "Storage ${fraction?.let { (it * 100).toInt() } ?: 0} percent used",
        onClick = onClick,
    )
}

@Composable
private fun NetworkCard(network: NetworkSnapshot?, downloads: List<Float>, modifier: Modifier, onClick: () -> Unit) {
    val colors = PulseTheme.colors
    InstrumentCard(modifier = modifier, accent = colors.network, onClick = onClick, onClickLabel = "Open network details") {
        SectionLabel("Network", accent = colors.network)
        Spacer(Modifier.height(10.dp))
        if (network != null && !network.trafficStatsSupported) {
            NotAvailable(compact = true)
        } else {
            SpeedLine(R.drawable.ic_arrow_down, "Down", network?.downloadBps, colors.network)
            Spacer(Modifier.height(6.dp))
            SpeedLine(R.drawable.ic_arrow_up, "Up", network?.uploadBps, colors.networkAlt)
            Spacer(Modifier.height(10.dp))
            val max = niceCeiling(downloads.maxOrNull() ?: 1f)
            LineChart(
                series = listOf(
                    ChartSeries(
                        name = "Download",
                        points = downloads.mapIndexed { i, v -> ChartPoint(i.toFloat(), v) },
                        color = colors.network,
                    ),
                ),
                xMin = 0f,
                xMax = 39f,
                yMin = 0f,
                yMax = max,
                contentDescription = "Download speed over the last 40 seconds",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = network?.let { Labels.transport(it.transport) } ?: "—",
                style = PulseTheme.type.caption,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun SpeedLine(icon: Int, label: String, bps: Double?, color: Color) {
    val colors = PulseTheme.colors
    val speed = Format.speed(bps)
    Row(verticalAlignment = Alignment.CenterVertically) {
        PulseIcon(icon, contentDescription = label, tint = color, size = 16.dp)
        Spacer(Modifier.width(6.dp))
        if (speed == null) {
            Text("Measuring…", style = PulseTheme.type.caption, color = colors.textTertiary)
        } else {
            AnimatedNumber(
                value = speed.value,
                style = PulseTheme.type.number.copy(fontSize = cappedSp(18f)),
                color = colors.textPrimary,
                decimals = if (speed.value >= 100f || speed.unit == "kbps") 0 else 1,
            )
            Spacer(Modifier.width(4.dp))
            Text(speed.unit, style = PulseTheme.type.caption, color = colors.textSecondary)
        }
    }
}

@Composable
private fun ThermalCard(
    cpu: CpuSnapshot?,
    batteryTempC: Float?,
    settings: AppSettings,
    fahrenheit: Boolean,
    onClick: () -> Unit,
) {
    val colors = PulseTheme.colors
    val level = cpu?.thermal
    val headroom = cpu?.thermalHeadroom
    val tint = when {
        level == null -> colors.cpu
        level.code >= ThermalLevel.SEVERE.code -> colors.heat
        level.code >= ThermalLevel.MODERATE.code -> colors.warning
        else -> colors.heatTint(colors.cpu, batteryTempC, settings.heatThresholdC)
    }
    InstrumentCard(
        modifier = Modifier.fillMaxWidth(),
        accent = tint,
        onClick = onClick,
        onClickLabel = "Open thermal details",
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Thermal", accent = tint, modifier = Modifier.weight(1f))
            if (level != null) Pill(Labels.thermal(level), tint, icon = R.drawable.ic_thermo)
        }
        Spacer(Modifier.height(12.dp))
        if (level == null) {
            NotAvailable()
        } else {
            Text(Labels.thermalExplain(level), style = PulseTheme.type.body, color = colors.textPrimary)
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Throttle headroom", style = PulseTheme.type.caption, color = colors.textSecondary, modifier = Modifier.weight(1f))
            Text(
                text = headroom?.let { "${(it * 100).toInt().coerceAtMost(999)}% of limit" } ?: "Not available on this device",
                style = PulseTheme.type.caption,
                color = if (headroom == null) colors.textTertiary else colors.textPrimary,
            )
        }
        Spacer(Modifier.height(6.dp))
        LinearMeter(fraction = headroom?.coerceAtMost(1f), color = tint)
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Battery ${Format.temp(batteryTempC, fahrenheit)}",
            style = PulseTheme.type.caption,
            color = colors.textSecondary,
        )
    }
}

@Composable
private fun HistoryTeaser(entries: List<HistoryEntity>, onClick: () -> Unit) {
    val colors = PulseTheme.colors
    InstrumentCard(
        modifier = Modifier.fillMaxWidth(),
        accent = colors.battery,
        onClick = onClick,
        onClickLabel = "Open history",
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Last 24 hours", accent = colors.battery, modifier = Modifier.weight(1f))
            PulseIcon(R.drawable.ic_chevron_right, contentDescription = null, tint = colors.textTertiary, size = 18.dp)
        }
        Spacer(Modifier.height(10.dp))
        if (entries.size < 2) {
            Text(
                "Collecting history. Pulse logs a point every 15 minutes, so the chart fills in over the day.",
                style = PulseTheme.type.caption,
                color = colors.textSecondary,
            )
        } else {
            val now = System.currentTimeMillis()
            val start = now - 24 * HOUR_MS
            LineChart(
                series = listOf(
                    ChartSeries(
                        name = "Battery",
                        points = entries.map { ChartPoint(((it.timestamp - start) / HOUR_MS.toFloat()), it.level.toFloat()) },
                        color = colors.battery,
                        gapX = 1f,
                    ),
                ),
                xMin = 0f,
                xMax = 24f,
                yMin = 0f,
                yMax = 100f,
                bands = chargingBands(entries, start),
                contentDescription = "Battery level over the last 24 hours",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
            )
        }
    }
}

/** Groups consecutive charging samples into shaded x-ranges (in hours since [start]). */
internal fun chargingBands(entries: List<HistoryEntity>, start: Long): List<ClosedFloatingPointRange<Float>> {
    val bands = mutableListOf<ClosedFloatingPointRange<Float>>()
    var bandStart: Float? = null
    var last = 0f
    for (e in entries) {
        val x = (e.timestamp - start) / HOUR_MS.toFloat()
        val open = bandStart
        if (e.charging) {
            if (open == null) bandStart = x
            last = x
        } else if (open != null) {
            bands += open..x
            bandStart = null
        }
    }
    val open = bandStart
    // A band that is still open extends one logging interval (15 min) past its last point.
    if (open != null) bands += open..(last + 0.25f)
    return bands
}
