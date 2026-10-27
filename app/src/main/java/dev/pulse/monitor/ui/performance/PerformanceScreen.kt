package dev.pulse.monitor.ui.performance

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import dev.pulse.monitor.data.model.CoreInfo
import dev.pulse.monitor.data.model.CpuSnapshot
import dev.pulse.monitor.data.model.ThermalLevel
import dev.pulse.monitor.data.settings.TempUnit
import dev.pulse.monitor.ui.Labels
import dev.pulse.monitor.ui.LocalAppContainer
import dev.pulse.monitor.ui.PerformanceViewModel
import dev.pulse.monitor.ui.components.AnimatedNumber
import dev.pulse.monitor.ui.components.CoreBars
import dev.pulse.monitor.ui.components.Divider
import dev.pulse.monitor.ui.components.InstrumentCard
import dev.pulse.monitor.ui.components.NotAvailable
import dev.pulse.monitor.ui.components.Pill
import dev.pulse.monitor.ui.components.RingGauge
import dev.pulse.monitor.ui.components.ScreenColumn
import dev.pulse.monitor.ui.components.ScreenHeader
import dev.pulse.monitor.ui.components.SectionLabel
import dev.pulse.monitor.ui.components.StatRow
import dev.pulse.monitor.ui.components.cappedSp
import dev.pulse.monitor.ui.theme.PulseTheme
import dev.pulse.monitor.ui.theme.heatTint
import dev.pulse.monitor.util.Format

@Composable
fun PerformanceScreen(padding: PaddingValues, onSettings: () -> Unit) {
    val container = LocalAppContainer.current
    val vm: PerformanceViewModel = viewModel { PerformanceViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = PulseTheme.colors
    val fahrenheit = state.settings.tempUnit == TempUnit.FAHRENHEIT

    ScreenColumn(padding) {
        item {
            ScreenHeader(title = "System", eyebrow = "CPU & thermals", accent = colors.cpu, onSettings = onSettings)
        }
        item { ThermalCard(state.cpu, state.batteryTempC, state.settings.heatThresholdC, fahrenheit) }
        item { CoresCard(state.cpu) }
        item { ChipCard(state.cpu) }
        item {
            InstrumentCard(Modifier.fillMaxWidth()) {
                SectionLabel("Why no CPU usage %?")
                Spacer(Modifier.height(8.dp))
                Text(
                    "Since Android 8, apps can't read how busy the whole processor is. Clock speed is the next best " +
                        "gauge, like a car's RPM: a core spinning near its top speed is working hard, one resting " +
                        "at its lowest speed is idle. The bars above show each core's speed as a share of its maximum.",
                    style = PulseTheme.type.body,
                    color = colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun ThermalCard(cpu: CpuSnapshot?, batteryTempC: Float?, hotAt: Float, fahrenheit: Boolean) {
    val colors = PulseTheme.colors
    val level = cpu?.thermal
    val headroom = cpu?.thermalHeadroom
    val tint = thermalColor(level, colors.cpu, colors.warning, colors.heat)
    InstrumentCard(Modifier.fillMaxWidth(), accent = tint) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Thermal status", accent = tint, modifier = Modifier.weight(1f))
            if (level != null) Pill(Labels.thermal(level), tint, icon = R.drawable.ic_thermo)
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val a11y = headroom?.let { "Thermal headroom ${(it * 100).toInt()} percent of the throttling limit" }
                ?: "Thermal headroom not available on this device"
            RingGauge(
                progress = headroom?.coerceIn(0f, 1f),
                color = tint,
                strokeWidth = 10.dp,
                ticks = 30,
                modifier = Modifier
                    .size(150.dp)
                    .clearAndSetSemantics { contentDescription = a11y },
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (headroom != null) {
                        AnimatedNumber(
                            value = headroom * 100f,
                            style = PulseTheme.type.bigNumber.copy(fontSize = cappedSp(28f)),
                            color = colors.textPrimary,
                            suffix = "%",
                        )
                        Text("of limit", style = PulseTheme.type.caption, color = colors.textSecondary)
                    } else {
                        Text("n/a", style = PulseTheme.type.bigNumber.copy(fontSize = cappedSp(28f)), color = colors.textTertiary)
                    }
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = level?.let { Labels.thermalExplain(it) } ?: "Thermal status is not available on this device.",
                    style = PulseTheme.type.body,
                    color = colors.textPrimary,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Headroom works like a fuel gauge in reverse: 100% means the phone has reached the point " +
                        "where it starts slowing down to cool off.",
                    style = PulseTheme.type.caption,
                    color = colors.textSecondary,
                )
            }
        }
        if (headroom == null) {
            Spacer(Modifier.height(8.dp))
            NotAvailable(compact = true, reason = "Thermal headroom (Android 11+ API) isn't supported here")
        }
        Spacer(Modifier.height(8.dp))
        Divider()
        StatRow(
            label = "Battery temperature",
            value = batteryTempC?.let { "${Format.temp(it, fahrenheit)} · ${Labels.tempFeel(it, hotAt)}" },
            icon = R.drawable.ic_thermo,
            valueColor = colors.heatTint(colors.textPrimary, batteryTempC, hotAt),
        )
    }
}

private fun thermalColor(level: ThermalLevel?, cool: Color, warm: Color, hot: Color): Color = when {
    level == null -> cool
    level.code >= ThermalLevel.SEVERE.code -> hot
    level.code >= ThermalLevel.MODERATE.code -> warm
    else -> cool
}

@Composable
private fun CoresCard(cpu: CpuSnapshot?) {
    val colors = PulseTheme.colors
    InstrumentCard(Modifier.fillMaxWidth(), accent = colors.cpu) {
        SectionLabel("Cores · live clock speed", accent = colors.cpu)
        Spacer(Modifier.height(14.dp))
        when {
            cpu == null -> Text("Reading…", style = PulseTheme.type.caption, color = colors.textTertiary)
            !cpu.frequencyAvailable -> {
                NotAvailable(reason = "This device blocks apps from reading CPU clock speeds.")
                Spacer(Modifier.height(8.dp))
                StatRow("Cores", cpu.cores.size.toString())
            }
            else -> {
                CoreBars(cpu.cores, colors.cpu, barHeight = 150.dp)
                Spacer(Modifier.height(12.dp))
                clusters(cpu.cores).forEachIndexed { i, cluster ->
                    if (i > 0) Divider()
                    StatRow(
                        label = cluster.name,
                        supporting = "${cluster.cores.size} × up to ${Format.frequency(cluster.maxKhz)}",
                        value = cluster.currentAvgKhz?.let { "now ${Format.frequency(it)}" } ?: "offline",
                        icon = R.drawable.ic_memory,
                        iconTint = colors.cpu,
                    )
                }
            }
        }
    }
}

private data class Cluster(val name: String, val cores: List<CoreInfo>, val maxKhz: Long?, val currentAvgKhz: Long?)

/** Groups cores by their top clock: the fastest group is "Prime", the slowest "Efficiency". */
private fun clusters(cores: List<CoreInfo>): List<Cluster> {
    val groups = cores.groupBy { it.maxKhz ?: -1L }.toSortedMap(Comparator.reverseOrder())
    val names = when (groups.size) {
        1 -> listOf("All cores")
        2 -> listOf("Performance", "Efficiency")
        3 -> listOf("Prime", "Performance", "Efficiency")
        else -> List(groups.size) { i -> if (i == 0) "Prime" else if (i == groups.size - 1) "Efficiency" else "Performance ${i}" }
    }
    return groups.entries.mapIndexed { i, (max, list) ->
        val online = list.mapNotNull { it.currentKhz }
        Cluster(
            name = names[i],
            cores = list,
            maxKhz = max.takeIf { it > 0 },
            currentAvgKhz = if (online.isEmpty()) null else online.sum() / online.size,
        )
    }
}

@Composable
private fun ChipCard(cpu: CpuSnapshot?) {
    val colors = PulseTheme.colors
    InstrumentCard(Modifier.fillMaxWidth(), accent = colors.cpu) {
        SectionLabel("Chip", accent = colors.cpu)
        Spacer(Modifier.height(4.dp))
        StatRow("System-on-chip", cpu?.socName, icon = R.drawable.ic_memory)
        Divider()
        StatRow("CPU cores", cpu?.cores?.size?.toString(), icon = R.drawable.ic_memory)
        Divider()
        StatRow(
            "Cores online",
            cpu?.let { c -> "${c.cores.count { it.online }} of ${c.cores.size}" },
            icon = R.drawable.ic_memory,
            supporting = "Idle cores can be switched off to save power",
        )
    }
}
