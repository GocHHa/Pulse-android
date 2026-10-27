package dev.pulse.monitor.alerts

import android.content.Context
import dev.pulse.monitor.data.model.BatterySnapshot
import dev.pulse.monitor.data.model.ThermalLevel
import dev.pulse.monitor.data.settings.AlertLatches
import dev.pulse.monitor.data.settings.AppSettings
import dev.pulse.monitor.data.settings.SettingsRepository
import dev.pulse.monitor.data.settings.TempUnit
import dev.pulse.monitor.util.Format
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Runs [AlertRules] against fresh readings and posts notifications. */
class AlertEngine(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
) {
    private val mutex = Mutex()

    suspend fun check(battery: BatterySnapshot, thermal: ThermalLevel?, storageFreePercent: Float?) {
        mutex.withLock {
            val settings = settingsRepository.current()
            val latches = settingsRepository.currentLatches()
            val input = AlertRules.Input(
                level = battery.levelPercent,
                plugged = battery.isPlugged,
                temperatureC = battery.temperatureC,
                thermal = thermal,
                storageFreePercent = storageFreePercent,
            )
            val (events, newLatches) = AlertRules.evaluate(input, settings, latches)
            if (newLatches != latches) settingsRepository.setLatches(newLatches)
            val fahrenheit = settings.tempUnit == TempUnit.FAHRENHEIT
            events.forEach { post(it, fahrenheit) }
        }
    }

    private fun post(event: AlertEvent, fahrenheit: Boolean) {
        when (event) {
            is AlertEvent.ChargeReached -> Notifications.postAlert(
                context, Notifications.ID_CHARGE,
                "Charged to ${event.level}%",
                "Your charge limit of ${event.target}% is reached. Unplugging now helps the battery age more slowly.",
            )
            is AlertEvent.Overheating -> {
                val parts = buildList {
                    event.temperatureC?.let { add("Battery is at ${Format.temp(it, fahrenheit)}.") }
                    event.thermal?.takeIf { it.code >= ThermalLevel.SEVERE.code }?.let {
                        add("Android reports ${it.name.lowercase()} thermal pressure and may slow the phone down.")
                    }
                    add("Let it cool: close heavy apps, remove the case or stop fast charging.")
                }
                Notifications.postAlert(context, Notifications.ID_HEAT, "Phone is running hot", parts.joinToString(" "))
            }
            is AlertEvent.LowStorage -> Notifications.postAlert(
                context, Notifications.ID_STORAGE,
                "Storage almost full",
                "Only ${Format.number(event.freePercent, 1)}% of storage is free. Low storage slows updates and app installs.",
            )
        }
    }
}
