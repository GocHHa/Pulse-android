package dev.pulse.monitor.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.pulse.monitor.R
import dev.pulse.monitor.data.settings.TempUnit
import dev.pulse.monitor.data.settings.ThemeMode
import dev.pulse.monitor.ui.LocalAppContainer
import dev.pulse.monitor.ui.SettingsViewModel
import dev.pulse.monitor.ui.components.Divider
import dev.pulse.monitor.ui.components.InstrumentCard
import dev.pulse.monitor.ui.components.PulseIcon
import dev.pulse.monitor.ui.components.ScreenColumn
import dev.pulse.monitor.ui.components.ScreenHeader
import dev.pulse.monitor.ui.components.SectionLabel
import dev.pulse.monitor.ui.components.SegmentedControl
import dev.pulse.monitor.ui.components.StatRow
import dev.pulse.monitor.ui.components.rememberHaptics
import dev.pulse.monitor.ui.theme.PulseTheme
import dev.pulse.monitor.util.Format
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(padding: PaddingValues, onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val vm: SettingsViewModel = viewModel { SettingsViewModel(container) }
    val settings by vm.state.collectAsStateWithLifecycle()
    val colors = PulseTheme.colors
    val context = LocalContext.current

    // Re-check notification permission whenever we come back (e.g. from system settings).
    var permissionTick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { permissionTick++ }
    val notificationsAllowed = remember(permissionTick) { notificationsGranted(context) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionTick++
    }
    val askNotifications = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            openNotificationSettings(context)
        }
    }

    ScreenColumn(padding) {
        item {
            ScreenHeader(title = "Settings", eyebrow = "Alerts & display", accent = colors.textSecondary, onBack = onBack)
        }
        val s = settings
        if (s == null) return@ScreenColumn

        item {
            InstrumentCard(Modifier.fillMaxWidth(), accent = colors.warning) {
                SectionLabel("Smart alerts", accent = colors.warning)
                Spacer(Modifier.height(4.dp))
                if (!notificationsAllowed) {
                    PermissionBanner(onAllow = askNotifications, onOpenSettings = { openNotificationSettings(context) })
                }
                AlertControl(
                    title = "Charge limit reached",
                    description = "Get a nudge to unplug. Stopping around 80% slows battery wear.",
                    enabled = s.chargeAlertEnabled,
                    onEnabled = vm::setChargeAlert,
                    value = s.chargeTargetPercent.toFloat(),
                    range = 50f..100f,
                    step = 5f,
                    label = { "${it.roundToInt()}%" },
                    onValue = { vm.setChargeTarget(it.roundToInt()) },
                    accent = colors.battery,
                )
                Divider()
                val fahrenheit = s.tempUnit == TempUnit.FAHRENHEIT
                AlertControl(
                    title = "Overheating",
                    description = "When the battery passes this temperature, or Android reports severe heat.",
                    enabled = s.heatAlertEnabled,
                    onEnabled = vm::setHeatAlert,
                    value = s.heatThresholdC,
                    range = 35f..50f,
                    step = 1f,
                    label = { Format.temp(it, fahrenheit, decimals = 0) },
                    onValue = { vm.setHeatThreshold(it) },
                    accent = colors.heat,
                )
                Divider()
                AlertControl(
                    title = "Low storage",
                    description = "When free space drops below this share of your storage.",
                    enabled = s.storageAlertEnabled,
                    onEnabled = vm::setStorageAlert,
                    value = s.storageThresholdPercent.toFloat(),
                    range = 5f..30f,
                    step = 1f,
                    label = { "${it.roundToInt()}% free" },
                    onValue = { vm.setStorageThreshold(it.roundToInt()) },
                    accent = colors.storage,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Alerts are checked every 15 minutes in the background, or every 5 seconds with Live monitoring on.",
                    style = PulseTheme.type.caption,
                    color = colors.textTertiary,
                )
            }
        }

        item {
            InstrumentCard(Modifier.fillMaxWidth(), accent = colors.battery) {
                SectionLabel("Live monitoring", accent = colors.battery)
                Spacer(Modifier.height(4.dp))
                ToggleRow(
                    title = "Persistent notification",
                    description = "Shows battery %, watts and temperature in your notification shade, updated " +
                        "every 5 seconds, and checks alerts continuously. Uses a little extra battery.",
                    checked = s.liveMonitoring,
                    accent = colors.battery,
                    onChange = { on ->
                        if (on && !notificationsAllowed) askNotifications()
                        vm.setLiveMonitoring(context, on)
                    },
                )
            }
        }

        item {
            InstrumentCard(Modifier.fillMaxWidth(), accent = colors.ram) {
                SectionLabel("Appearance", accent = colors.ram)
                Spacer(Modifier.height(12.dp))
                Text("Theme", style = PulseTheme.type.body, color = colors.textSecondary)
                Spacer(Modifier.height(8.dp))
                val themes = listOf(ThemeMode.DARK, ThemeMode.LIGHT, ThemeMode.SYSTEM)
                SegmentedControl(
                    options = listOf("Dark", "Light", "System"),
                    selectedIndex = themes.indexOf(s.themeMode).coerceAtLeast(0),
                    onSelect = { vm.setTheme(themes[it]) },
                    accent = colors.ram,
                )
                Spacer(Modifier.height(16.dp))
                Text("Temperature unit", style = PulseTheme.type.body, color = colors.textSecondary)
                Spacer(Modifier.height(8.dp))
                SegmentedControl(
                    options = listOf("Celsius °C", "Fahrenheit °F"),
                    selectedIndex = if (s.tempUnit == TempUnit.CELSIUS) 0 else 1,
                    onSelect = { vm.setTempUnit(if (it == 0) TempUnit.CELSIUS else TempUnit.FAHRENHEIT) },
                    accent = colors.ram,
                )
            }
        }

        item {
            InstrumentCard(Modifier.fillMaxWidth()) {
                SectionLabel("About")
                Spacer(Modifier.height(4.dp))
                StatRow("Version", appVersion(context), icon = R.drawable.ic_info)
                Divider()
                StatRow(
                    "Notifications",
                    if (notificationsAllowed) "Allowed" else "Blocked",
                    icon = R.drawable.ic_bell,
                    valueColor = if (notificationsAllowed) colors.battery else colors.warning,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Pulse reads everything on-device and sends nothing anywhere. History older than 7 days is " +
                        "deleted automatically. Fonts: Saira and Inter (SIL Open Font License).",
                    style = PulseTheme.type.caption,
                    color = colors.textTertiary,
                )
            }
        }
    }
}

@Composable
private fun PermissionBanner(onAllow: () -> Unit, onOpenSettings: () -> Unit) {
    val colors = PulseTheme.colors
    Column(Modifier.padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PulseIcon(R.drawable.ic_bell, contentDescription = null, tint = colors.warning, size = 18.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                "Notifications are off, so alerts can't reach you.",
                style = PulseTheme.type.bodyStrong,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
        }
        Row {
            TextButton(onClick = onAllow) { Text("Allow", color = colors.warning) }
            TextButton(onClick = onOpenSettings) { Text("Open settings", color = colors.textSecondary) }
        }
        Divider()
    }
}

@Composable
private fun ToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    accent: Color,
    onChange: (Boolean) -> Unit,
) {
    val colors = PulseTheme.colors
    val haptics = rememberHaptics()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch) {
                haptics.toggle(it)
                onChange(it)
            }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = PulseTheme.type.bodyStrong, color = colors.textPrimary)
            Text(description, style = PulseTheme.type.caption, color = colors.textSecondary)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = null, // the whole row is the toggle target
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = accent,
                uncheckedThumbColor = colors.textSecondary,
                uncheckedTrackColor = colors.surface3,
                uncheckedBorderColor = colors.outline,
            ),
        )
    }
}

@Composable
private fun AlertControl(
    title: String,
    description: String,
    enabled: Boolean,
    onEnabled: (Boolean) -> Unit,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    label: (Float) -> String,
    onValue: (Float) -> Unit,
    accent: Color,
) {
    val colors = PulseTheme.colors
    val haptics = rememberHaptics()
    ToggleRow(title, description, enabled, accent, onEnabled)
    // Local slider position so dragging is smooth; saved when the finger lifts.
    var local by remember(value) { mutableFloatStateOf(value) }
    val steps = ((range.endInclusive - range.start) / step).roundToInt() - 1
    Row(verticalAlignment = Alignment.CenterVertically) {
        Slider(
            value = local,
            onValueChange = { v ->
                val snapped = (Math.round((v - range.start) / step) * step + range.start).coerceIn(range)
                if (snapped != local) haptics.tick()
                local = snapped
            },
            onValueChangeFinished = { onValue(local) },
            valueRange = range,
            steps = steps,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent,
                inactiveTrackColor = colors.track,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
            modifier = Modifier
                .weight(1f)
                .semantics {
                    contentDescription = "$title threshold"
                    stateDescription = label(local)
                },
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = label(local),
            style = PulseTheme.type.smallNumber,
            color = if (enabled) colors.textPrimary else colors.textTertiary,
            modifier = Modifier.width(72.dp),
        )
    }
    Spacer(Modifier.height(6.dp))
}

private fun notificationsGranted(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) return false
    return androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
}

private fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

private fun appVersion(context: Context): String? = runCatching {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName
}.getOrNull()
