package dev.pulse.monitor.ui.network

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.pulse.monitor.R
import dev.pulse.monitor.data.model.NetworkSnapshot
import dev.pulse.monitor.data.model.Transport
import dev.pulse.monitor.ui.Labels
import dev.pulse.monitor.ui.LocalAppContainer
import dev.pulse.monitor.ui.NetworkViewModel
import dev.pulse.monitor.ui.SpeedSample
import dev.pulse.monitor.ui.components.AnimatedNumber
import dev.pulse.monitor.ui.components.ChartPoint
import dev.pulse.monitor.ui.components.ChartSeries
import dev.pulse.monitor.ui.components.Divider
import dev.pulse.monitor.ui.components.InstrumentCard
import dev.pulse.monitor.ui.components.LineChart
import dev.pulse.monitor.ui.components.NotAvailable
import dev.pulse.monitor.ui.components.Pill
import dev.pulse.monitor.ui.components.PulseIcon
import dev.pulse.monitor.ui.components.ScreenColumn
import dev.pulse.monitor.ui.components.ScreenHeader
import dev.pulse.monitor.ui.components.SectionLabel
import dev.pulse.monitor.ui.components.SignalBars
import dev.pulse.monitor.ui.components.StatRow
import dev.pulse.monitor.ui.components.cappedSp
import dev.pulse.monitor.ui.components.niceCeiling
import dev.pulse.monitor.ui.theme.PulseTheme
import dev.pulse.monitor.util.Format

@Composable
fun NetworkScreen(padding: PaddingValues, onSettings: () -> Unit) {
    val container = LocalAppContainer.current
    val vm: NetworkViewModel = viewModel { NetworkViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = PulseTheme.colors
    val network = state.network

    ScreenColumn(padding) {
        item {
            ScreenHeader(title = "Network", eyebrow = "Throughput & signal", accent = colors.network, onSettings = onSettings)
        }
        item { LiveSpeedCard(network, state.samples) }
        item { ConnectionCard(network) }
        item { WifiCard(network) }
        item { CellCard(network) }
    }
}

@Composable
private fun LiveSpeedCard(network: NetworkSnapshot?, samples: List<SpeedSample>) {
    val colors = PulseTheme.colors
    InstrumentCard(Modifier.fillMaxWidth(), accent = colors.network) {
        SectionLabel("Live speed", accent = colors.network)
        Spacer(Modifier.height(12.dp))
        if (network != null && !network.trafficStatsSupported) {
            NotAvailable(reason = "This device doesn't expose traffic counters")
        } else {
            SpeedContent(network, samples)
        }
    }
}

@Composable
private fun SpeedContent(network: NetworkSnapshot?, samples: List<SpeedSample>) {
    val colors = PulseTheme.colors
    Column {
        Row(Modifier.fillMaxWidth()) {
            BigSpeed("Download", R.drawable.ic_arrow_down, network?.downloadBps, colors.network, Modifier.weight(1f))
            BigSpeed("Upload", R.drawable.ic_arrow_up, network?.uploadBps, colors.networkAlt, Modifier.weight(1f))
        }
        Spacer(Modifier.height(16.dp))

        // The graph scrolls smoothly: the visible window glides one second to the left over
        // exactly one second, so new points slide in instead of popping.
        val latest = samples.lastOrNull()?.seq ?: 0L
        val head = remember { Animatable(0f) }
        LaunchedEffect(latest) {
            if (head.value == 0f || latest - head.value > 3f) {
                head.snapTo(latest.toFloat())
            } else {
                head.animateTo(latest.toFloat(), tween(durationMillis = 1000, easing = LinearEasing))
            }
        }
        val window = NetworkViewModel.WINDOW.toFloat()
        val down = samples.map { ChartPoint(it.seq.toFloat(), mbps(it.downBps)) }
        val up = samples.map { ChartPoint(it.seq.toFloat(), mbps(it.upBps)) }
        val visibleMax = (down + up).filter { it.x >= latest - window }.maxOfOrNull { it.y } ?: 0f
        val target = maxOf(niceCeiling(visibleMax), 0.1f)
        val yMax by animateFloatAsState(target, tween(600), label = "netScale")

        LineChart(
            series = listOf(
                ChartSeries("Download", down, colors.network),
                ChartSeries("Upload", up, colors.networkAlt, fill = false, dashed = true),
            ),
            xMin = head.value - window,
            xMax = head.value,
            yMin = 0f,
            yMax = yMax,
            yLabels = listOf(
                target to mbpsLabel(target),
                target / 2 to mbpsLabel(target / 2),
                0f to "0",
            ),
            contentDescription = "Download and upload speed over the last minute",
            scrubLabel = { _, picked ->
                picked.joinToString("  ") { (s, p) -> "${s.name.first()}: ${Format.number(p.y, 2)} Mbps" }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp),
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            LegendSwatch(colors.network, "Download (solid)")
            Spacer(Modifier.width(16.dp))
            LegendSwatch(colors.networkAlt, "Upload (dashed)")
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Counts every app's traffic, measured each second. Units are megabits per second, like a speed test.",
            style = PulseTheme.type.caption,
            color = colors.textTertiary,
        )
    }
}

private fun mbps(bytesPerSecond: Float): Float = bytesPerSecond * 8f / 1_000_000f

private fun mbpsLabel(v: Float): String = Format.number(v, if (v < 10f) 1 else 0)

@Composable
private fun LegendSwatch(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.Canvas(Modifier.width(14.dp).height(3.dp)) { drawRect(color) }
        Spacer(Modifier.width(6.dp))
        Text(label, style = PulseTheme.type.caption, color = PulseTheme.colors.textSecondary)
    }
}

@Composable
private fun BigSpeed(label: String, icon: Int, bps: Double?, color: Color, modifier: Modifier) {
    val colors = PulseTheme.colors
    val speed = Format.speed(bps)
    Column(
        modifier.clearAndSetSemantics {
            contentDescription = "$label ${speed?.let { "${Format.number(it.value, 1)} ${it.unit}" } ?: "measuring"}"
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PulseIcon(icon, contentDescription = null, tint = color, size = 16.dp)
            Spacer(Modifier.width(4.dp))
            Text(label.uppercase(), style = PulseTheme.type.sectionLabel, color = colors.textSecondary)
        }
        Row(verticalAlignment = Alignment.Bottom) {
            AnimatedNumber(
                value = speed?.value,
                style = PulseTheme.type.bigNumber.copy(fontSize = cappedSp(34f)),
                color = colors.textPrimary,
                decimals = if (speed == null || speed.value >= 100f || speed.unit == "kbps") 0 else 1,
                placeholder = "…",
            )
            Spacer(Modifier.width(4.dp))
            Text(
                speed?.unit ?: "",
                style = PulseTheme.type.caption,
                color = color,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
    }
}

@Composable
private fun ConnectionCard(n: NetworkSnapshot?) {
    val colors = PulseTheme.colors
    InstrumentCard(Modifier.fillMaxWidth(), accent = colors.network) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Connection", accent = colors.network, modifier = Modifier.weight(1f))
            if (n != null) {
                Pill(
                    text = if (n.internetValidated) "Online" else if (n.transport == Transport.NONE) "Offline" else "No internet",
                    color = if (n.internetValidated) colors.battery else colors.warning,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        StatRow("Active network", n?.let { Labels.transport(it.transport) }, icon = R.drawable.ic_nav_network, iconTint = colors.network)
        Divider()
        StatRow(
            "Metered",
            n?.let { if (it.metered) "Yes (data may cost money)" else "No" },
            icon = R.drawable.ic_nav_network,
            iconTint = colors.network,
        )
    }
}

@Composable
private fun WifiCard(n: NetworkSnapshot?) {
    val colors = PulseTheme.colors
    val wifi = n?.wifi
    InstrumentCard(Modifier.fillMaxWidth(), accent = colors.network) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Wi-Fi", accent = colors.network, modifier = Modifier.weight(1f))
            if (wifi != null) SignalBars(wifi.signalLevel, colors.network)
        }
        Spacer(Modifier.height(4.dp))
        if (n == null) {
            Text("Reading…", style = PulseTheme.type.caption, color = colors.textTertiary)
        } else if (wifi == null) {
            Text(
                if (n.transport == Transport.WIFI) NOT_AVAILABLE_TEXT else "Not connected to Wi-Fi",
                style = PulseTheme.type.body,
                color = colors.textSecondary,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        } else {
            StatRow(
                "Signal",
                wifi.rssiDbm?.let { "$it dBm · ${Labels.signalQuality(wifi.signalLevel) ?: ""}".trimEnd(' ', '·') },
                supporting = "Closer to 0 dBm is stronger (−50 great, −80 weak)",
                icon = R.drawable.ic_nav_network,
                iconTint = colors.network,
            )
            Divider()
            StatRow("Link speed", wifi.linkSpeedMbps?.let { "$it Mbps" }, icon = R.drawable.ic_nav_network, iconTint = colors.network)
            Divider()
            StatRow(
                "Up / down link",
                if (wifi.txLinkSpeedMbps != null || wifi.rxLinkSpeedMbps != null) {
                    "${wifi.txLinkSpeedMbps ?: "—"} / ${wifi.rxLinkSpeedMbps ?: "—"} Mbps"
                } else null,
                icon = R.drawable.ic_nav_network,
                iconTint = colors.network,
            )
            Divider()
            StatRow(
                "Band",
                wifi.band?.let { b -> wifi.frequencyMhz?.let { "$b · $it MHz" } ?: b },
                icon = R.drawable.ic_nav_network,
                iconTint = colors.network,
            )
            Divider()
            StatRow("Standard", wifi.standard, icon = R.drawable.ic_nav_network, iconTint = colors.network)
        }
    }
}

@Composable
private fun CellCard(n: NetworkSnapshot?) {
    val colors = PulseTheme.colors
    val cell = n?.cell
    InstrumentCard(Modifier.fillMaxWidth(), accent = colors.network) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Mobile", accent = colors.network, modifier = Modifier.weight(1f))
            if (cell != null) SignalBars(cell.signalLevel, colors.network)
        }
        Spacer(Modifier.height(4.dp))
        when {
            n == null -> Text("Reading…", style = PulseTheme.type.caption, color = colors.textTertiary)
            !n.hasTelephony -> NotAvailable()
            cell == null -> Text(
                "No mobile signal detected (no SIM, airplane mode, or the modem isn't reporting).",
                style = PulseTheme.type.body,
                color = colors.textSecondary,
            )
            else -> {
                StatRow(
                    "Signal",
                    cell.dbm?.let { "$it dBm · ${Labels.signalQuality(cell.signalLevel) ?: ""}".trimEnd(' ', '·') }
                        ?: Labels.signalQuality(cell.signalLevel),
                    supporting = "Closer to 0 dBm is stronger (−80 great, −115 weak)",
                    icon = R.drawable.ic_cell,
                    iconTint = colors.network,
                )
                Divider()
                StatRow("Technology", cell.technology, icon = R.drawable.ic_cell, iconTint = colors.network)
                Divider()
                StatRow("Operator", cell.operatorName, icon = R.drawable.ic_cell, iconTint = colors.network)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Technology is read from the radio's signal reports, so no phone or location permission is needed.",
                    style = PulseTheme.type.caption,
                    color = colors.textTertiary,
                )
            }
        }
    }
}

private const val NOT_AVAILABLE_TEXT = "Wi-Fi details: not available on this device"
