package dev.pulse.monitor.data.settings

enum class ThemeMode { DARK, LIGHT, SYSTEM }

enum class TempUnit { CELSIUS, FAHRENHEIT }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val tempUnit: TempUnit = TempUnit.CELSIUS,
    val chargeAlertEnabled: Boolean = true,
    val chargeTargetPercent: Int = 80,
    val heatAlertEnabled: Boolean = true,
    val heatThresholdC: Float = 42f,
    val storageAlertEnabled: Boolean = true,
    val storageThresholdPercent: Int = 10,
    val liveMonitoring: Boolean = false,
    val askedNotificationPermission: Boolean = false,
)

/** Remembers which alerts already fired so we notify once per event, not every check. */
data class AlertLatches(
    val charge: Boolean = false,
    val heat: Boolean = false,
    val storage: Boolean = false,
)
