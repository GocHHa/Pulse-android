package dev.pulse.monitor.ui.memory

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
import dev.pulse.monitor.data.model.MemorySnapshot
import dev.pulse.monitor.ui.LocalAppContainer
import dev.pulse.monitor.ui.MemoryViewModel
import dev.pulse.monitor.ui.components.AnimatedNumber
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
import dev.pulse.monitor.util.Format

@Composable
fun MemoryScreen(padding: PaddingValues, onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val vm: MemoryViewModel = viewModel { MemoryViewModel(container) }
    val memory by vm.state.collectAsStateWithLifecycle()
    val colors = PulseTheme.colors

    ScreenColumn(padding) {
        item {
            ScreenHeader(title = "Memory & Storage", eyebrow = "RAM · flash", accent = colors.ram, onBack = onBack)
        }
        item { RamCard(memory) }
        item { StorageCard(memory) }
    }
}

@Composable
private fun GaugeHeader(
    fraction: Float?,
    color: Color,
    bigLabel: String,
    smallLabel: String,
    a11y: String,
) {
    val colors = PulseTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        RingGauge(
            progress = fraction,
            color = color,
            strokeWidth = 12.dp,
            ticks = 30,
            modifier = Modifier
                .size(160.dp)
                .clearAndSetSemantics { contentDescription = a11y },
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AnimatedNumber(
                    value = fraction?.times(100f),
                    style = PulseTheme.type.bigNumber.copy(fontSize = cappedSp(30f)),
                    color = colors.textPrimary,
                    suffix = "%",
                    placeholder = "n/a",
                )
                Text("used", style = PulseTheme.type.caption, color = colors.textSecondary)
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(bigLabel, style = PulseTheme.type.number, color = colors.textPrimary)
            Text(smallLabel, style = PulseTheme.type.caption, color = colors.textSecondary)
        }
    }
}

@Composable
private fun RamCard(m: MemorySnapshot?) {
    val colors = PulseTheme.colors
    InstrumentCard(Modifier.fillMaxWidth(), accent = colors.ram) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("RAM", accent = colors.ram, modifier = Modifier.weight(1f))
            if (m?.isLowMemory == true) Pill("Low memory", colors.warning, icon = R.drawable.ic_warning)
        }
        Spacer(Modifier.height(12.dp))
        if (m != null && m.totalRamBytes == null) {
            NotAvailable()
        } else {
            val fraction = m?.ramUsedFraction
            GaugeHeader(
                fraction = fraction,
                color = colors.ram,
                bigLabel = "${Format.bytesBinary(m?.availableRamBytes)} free",
                smallLabel = "of ${Format.bytesBinary(m?.totalRamBytes)} usable",
                a11y = "RAM ${fraction?.let { (it * 100).toInt() } ?: 0} percent used, " +
                    "${Format.bytesBinary(m?.availableRamBytes)} available",
            )
            Spacer(Modifier.height(8.dp))
            StatRow("In use", Format.bytesBinary(m?.usedRamBytes), icon = R.drawable.ic_memory, iconTint = colors.ram)
            Divider()
            StatRow("Available now", Format.bytesBinary(m?.availableRamBytes), icon = R.drawable.ic_memory, iconTint = colors.ram)
            Divider()
            StatRow(
                "Installed",
                m?.advertisedRamBytes?.let { Format.bytesBinary(it) },
                icon = R.drawable.ic_memory,
                iconTint = colors.ram,
                supporting = "As advertised (Android 14+). Usable is lower: the system reserves some.",
            )
            Divider()
            StatRow(
                "Low-memory line",
                m?.lowMemoryThresholdBytes?.let { Format.bytesBinary(it) },
                icon = R.drawable.ic_warning,
                iconTint = colors.ram,
                supporting = "Below this, Android starts closing background apps",
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "A mostly-full RAM bar is normal. Android keeps recently used apps in memory, like keeping " +
                    "tools on the workbench instead of back in the drawer, so they reopen instantly. It frees " +
                    "space the moment something needs it.",
                style = PulseTheme.type.caption,
                color = colors.textTertiary,
            )
        }
    }
}

@Composable
private fun StorageCard(m: MemorySnapshot?) {
    val colors = PulseTheme.colors
    InstrumentCard(Modifier.fillMaxWidth(), accent = colors.storage) {
        SectionLabel("Internal storage", accent = colors.storage)
        Spacer(Modifier.height(12.dp))
        if (m != null && m.storageTotalBytes == null) {
            NotAvailable()
        } else {
            val fraction = m?.storageUsedFraction
            GaugeHeader(
                fraction = fraction,
                color = colors.storage,
                bigLabel = "${Format.bytesDecimal(m?.storageAvailableBytes)} free",
                smallLabel = "of ${Format.bytesDecimal(m?.storageTotalBytes)} for apps & files",
                a11y = "Storage ${fraction?.let { (it * 100).toInt() } ?: 0} percent used, " +
                    "${Format.bytesDecimal(m?.storageAvailableBytes)} free",
            )
            Spacer(Modifier.height(8.dp))
            StatRow("Used", Format.bytesDecimal(m?.storageUsedBytes), icon = R.drawable.ic_storage, iconTint = colors.storage)
            Divider()
            StatRow("Free", Format.bytesDecimal(m?.storageAvailableBytes), icon = R.drawable.ic_storage, iconTint = colors.storage)
            Divider()
            StatRow(
                "Free share",
                m?.storageFreePercent?.let { "${Format.number(it, 1)}%" },
                icon = R.drawable.ic_storage,
                iconTint = colors.storage,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "This is the user data partition. The phone's box capacity also includes Android itself, " +
                    "which lives on a separate, read-only partition.",
                style = PulseTheme.type.caption,
                color = colors.textTertiary,
            )
        }
    }
}
