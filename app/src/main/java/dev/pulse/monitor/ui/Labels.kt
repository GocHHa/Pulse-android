package dev.pulse.monitor.ui

import dev.pulse.monitor.data.battery.CurrentNormalizer
import dev.pulse.monitor.data.battery.DrainTracker
import dev.pulse.monitor.data.model.BatteryHealth
import dev.pulse.monitor.data.model.BatterySnapshot
import dev.pulse.monitor.data.model.ChargeStatus
import dev.pulse.monitor.data.model.PlugType
import dev.pulse.monitor.data.model.ThermalLevel
import dev.pulse.monitor.data.model.Transport
import dev.pulse.monitor.util.Format

/** Human-friendly wording shared across screens, notifications and accessibility text. */
object Labels {

    fun plug(plug: PlugType): String = when (plug) {
        PlugType.AC -> "Wall charger"
        PlugType.USB -> "USB"
        PlugType.WIRELESS -> "Wireless"
        PlugType.DOCK -> "Dock"
        PlugType.UNKNOWN -> "Power source"
        PlugType.NONE -> "Unplugged"
    }

    fun status(b: BatterySnapshot): String = when (b.status) {
        ChargeStatus.CHARGING -> "Charging · ${plug(b.plug)}"
        ChargeStatus.FULL -> if (b.isPlugged) "Full · ${plug(b.plug)}" else "Full"
        ChargeStatus.NOT_CHARGING -> if (b.isPlugged) "Plugged in · not charging" else "Not charging"
        ChargeStatus.DISCHARGING -> "On battery"
        ChargeStatus.UNKNOWN -> if (b.isPlugged) "Plugged in" else "On battery"
    }

    fun health(h: BatteryHealth): String = when (h) {
        BatteryHealth.GOOD -> "Good"
        BatteryHealth.OVERHEAT -> "Overheating"
        BatteryHealth.DEAD -> "Dead"
        BatteryHealth.OVER_VOLTAGE -> "Over-voltage"
        BatteryHealth.FAILURE -> "Failure reported"
        BatteryHealth.COLD -> "Too cold"
        BatteryHealth.UNKNOWN -> "Unknown"
    }

    fun thermal(level: ThermalLevel): String = when (level) {
        ThermalLevel.NONE -> "Normal"
        ThermalLevel.LIGHT -> "Light"
        ThermalLevel.MODERATE -> "Moderate"
        ThermalLevel.SEVERE -> "Severe"
        ThermalLevel.CRITICAL -> "Critical"
        ThermalLevel.EMERGENCY -> "Emergency"
        ThermalLevel.SHUTDOWN -> "Shutdown imminent"
    }

    fun thermalExplain(level: ThermalLevel): String = when (level) {
        ThermalLevel.NONE -> "Running cool. No performance limits."
        ThermalLevel.LIGHT -> "Slightly warm. Android may trim background work."
        ThermalLevel.MODERATE -> "Warm. Some apps may lower frame rates or quality."
        ThermalLevel.SEVERE -> "Hot. The system is slowing the chip to cool down."
        ThermalLevel.CRITICAL -> "Very hot. Performance is heavily limited."
        ThermalLevel.EMERGENCY -> "Dangerously hot. Features are being shut off."
        ThermalLevel.SHUTDOWN -> "The phone is about to power off to protect itself."
    }

    /** Short temperature feeling, so meaning never relies on colour alone. */
    fun tempFeel(c: Float?, hotAt: Float): String? {
        c ?: return null
        return when {
            c >= hotAt -> "Hot"
            c >= hotAt - 6f -> "Warm"
            c < 5f -> "Cold"
            else -> "Normal"
        }
    }

    fun transport(t: Transport): String = when (t) {
        Transport.WIFI -> "Wi-Fi"
        Transport.CELLULAR -> "Mobile data"
        Transport.ETHERNET -> "Ethernet"
        Transport.VPN -> "VPN"
        Transport.OTHER -> "Other"
        Transport.NONE -> "Offline"
    }

    fun signalQuality(level: Int?): String? = when (level) {
        null -> null
        4 -> "Excellent"
        3 -> "Good"
        2 -> "Fair"
        1 -> "Poor"
        else -> "Very poor"
    }

    fun drain(estimate: DrainTracker.Estimate): String = when (estimate) {
        is DrainTracker.Estimate.Rate -> "${Format.number(estimate.percentPerHour, 1)} %/h"
        is DrainTracker.Estimate.Below -> "under ${Format.number(estimate.maxPercentPerHour, 1)} %/h"
        is DrainTracker.Estimate.Measuring -> "Measuring…"
        DrainTracker.Estimate.NotDischarging -> "Not draining"
    }

    fun sensorUnit(unit: CurrentNormalizer.CurrentUnit?): String? = when (unit) {
        CurrentNormalizer.CurrentUnit.MICROAMPS -> "Microamps (Android standard)"
        CurrentNormalizer.CurrentUnit.MILLIAMPS -> "Milliamps (auto-converted)"
        null -> null
    }

    fun signMode(mode: CurrentNormalizer.SignMode): String = when (mode) {
        CurrentNormalizer.SignMode.STANDARD -> "Standard (+ while charging)"
        CurrentNormalizer.SignMode.INVERTED -> "Inverted (auto-corrected)"
        CurrentNormalizer.SignMode.MAGNITUDE_ONLY -> "Size only (direction from charge status)"
        CurrentNormalizer.SignMode.LEARNING -> "Learning (plug and unplug once to calibrate)"
    }

    /** The "time" readout: time to full when charging, time left when draining. */
    fun timeReadout(b: BatterySnapshot): Pair<String, String?> = when {
        b.isCharging -> "To full" to b.chargeTimeRemainingMs?.let { Format.duration(it) }
        !b.isPlugged -> "Time left" to b.timeLeftOnBatteryMs?.let { Format.duration(it) }
        else -> "Status" to if (b.status == ChargeStatus.FULL) "Full" else "Holding"
    }

    fun batteryA11y(b: BatterySnapshot?, fahrenheit: Boolean): String {
        b ?: return "Battery reading loading"
        val parts = mutableListOf<String>()
        parts += b.levelPercent?.let { "Battery $it percent" } ?: "Battery level not available"
        parts += status(b)
        b.powerW?.let { parts += "${Format.number(kotlin.math.abs(it), 1)} watts ${if (it >= 0) "in" else "out"}" }
        b.temperatureC?.let { parts += "temperature ${Format.temp(it, fahrenheit)}" }
        return parts.joinToString(", ")
    }
}
