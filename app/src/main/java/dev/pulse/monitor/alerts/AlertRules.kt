package dev.pulse.monitor.alerts

import dev.pulse.monitor.data.model.ThermalLevel
import dev.pulse.monitor.data.settings.AlertLatches
import dev.pulse.monitor.data.settings.AppSettings

/** What the rules decided should be announced. */
sealed interface AlertEvent {
    data class ChargeReached(val level: Int, val target: Int) : AlertEvent
    data class Overheating(val temperatureC: Float?, val thermal: ThermalLevel?) : AlertEvent
    data class LowStorage(val freePercent: Float) : AlertEvent
}

/**
 * Pure alert logic, separated from Android so it's easy to reason about.
 *
 * Each alert works like a latch: it fires once when the condition becomes true and then
 * stays quiet until the condition has clearly gone away again (a small "hysteresis" gap
 * stops it from re-firing when a value hovers around the threshold).
 */
object AlertRules {
    data class Input(
        val level: Int?,
        val plugged: Boolean,
        val temperatureC: Float?,
        val thermal: ThermalLevel?,
        val storageFreePercent: Float?,
    )

    fun evaluate(input: Input, settings: AppSettings, latches: AlertLatches): Pair<List<AlertEvent>, AlertLatches> {
        val events = mutableListOf<AlertEvent>()
        var charge = latches.charge
        var heat = latches.heat
        var storage = latches.storage

        // Charge limit: only meaningful while plugged in.
        val level = input.level
        if (settings.chargeAlertEnabled && level != null && input.plugged) {
            if (!charge && level >= settings.chargeTargetPercent) {
                events += AlertEvent.ChargeReached(level, settings.chargeTargetPercent)
                charge = true
            } else if (charge && level < settings.chargeTargetPercent - 3) {
                charge = false
            }
        } else if (!input.plugged) {
            charge = false
        }

        // Overheating: battery temperature over the threshold, or Android says "severe" or worse.
        val temp = input.temperatureC
        val thermalHot = input.thermal != null && input.thermal.code >= ThermalLevel.SEVERE.code
        val tempHot = temp != null && temp >= settings.heatThresholdC
        if (settings.heatAlertEnabled) {
            if (!heat && (tempHot || thermalHot)) {
                events += AlertEvent.Overheating(temp, input.thermal)
                heat = true
            } else if (heat && !thermalHot && (temp == null || temp < settings.heatThresholdC - 2f)) {
                heat = false
            }
        }

        // Low storage.
        val free = input.storageFreePercent
        if (settings.storageAlertEnabled && free != null) {
            if (!storage && free < settings.storageThresholdPercent) {
                events += AlertEvent.LowStorage(free)
                storage = true
            } else if (storage && free > settings.storageThresholdPercent + 2) {
                storage = false
            }
        }

        return events to AlertLatches(charge = charge, heat = heat, storage = storage)
    }
}
