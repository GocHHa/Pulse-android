package dev.pulse.monitor.data.battery

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import dev.pulse.monitor.data.model.BatteryHealth
import dev.pulse.monitor.data.model.BatterySnapshot
import dev.pulse.monitor.data.model.ChargeStatus
import dev.pulse.monitor.data.model.PlugType

/**
 * Reads the battery through the sticky ACTION_BATTERY_CHANGED broadcast plus BatteryManager
 * properties. Cheap enough to call once per second.
 */
class BatteryReader(
    context: Context,
    private val normalizer: CurrentNormalizer,
    private val drainTracker: DrainTracker,
) {
    private val appContext = context.applicationContext
    private val batteryManager = appContext.getSystemService(Context.BATTERY_SERVICE) as BatteryManager

    fun read(nowMs: Long = System.currentTimeMillis()): BatterySnapshot {
        // Passing a null receiver just returns the last sticky broadcast; nothing is registered.
        val intent: Intent? = appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

        val level = levelPercent(intent)
        val status = when (intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)) {
            BatteryManager.BATTERY_STATUS_CHARGING -> ChargeStatus.CHARGING
            BatteryManager.BATTERY_STATUS_DISCHARGING -> ChargeStatus.DISCHARGING
            BatteryManager.BATTERY_STATUS_FULL -> ChargeStatus.FULL
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> ChargeStatus.NOT_CHARGING
            else -> ChargeStatus.UNKNOWN
        }
        val plug = plugType(intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1)
        val plugged = plug != PlugType.NONE

        val voltageMv = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)?.let { v ->
            when {
                v <= 0 -> null
                v < 100 -> v * 1000 // some firmwares report volts
                else -> v
            }
        }

        val rawCurrent = runCatching {
            batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        }.getOrNull()
        val currentMa = normalizer.normalize(rawCurrent, plugged, status == ChargeStatus.CHARGING)
        val powerW = if (currentMa != null && voltageMv != null) (voltageMv / 1000f) * (currentMa / 1000f) else null

        val temperatureC = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)?.let { t ->
            if (t == Int.MIN_VALUE || t <= -400 || t >= 1000) null else t / 10f
        }

        val health = when (intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)) {
            BatteryManager.BATTERY_HEALTH_GOOD -> BatteryHealth.GOOD
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> BatteryHealth.OVERHEAT
            BatteryManager.BATTERY_HEALTH_DEAD -> BatteryHealth.DEAD
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> BatteryHealth.OVER_VOLTAGE
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> BatteryHealth.FAILURE
            BatteryManager.BATTERY_HEALTH_COLD -> BatteryHealth.COLD
            else -> BatteryHealth.UNKNOWN
        }

        val cycleSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
        val cycleCount = if (cycleSupported) {
            intent?.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1)?.takeIf { it >= 0 }
        } else null

        val technology = intent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY)?.takeIf { it.isNotBlank() }

        val chargeTimeMs = if (status == ChargeStatus.CHARGING) {
            runCatching { batteryManager.computeChargeTimeRemaining() }.getOrNull()?.takeIf { it > 0 }
        } else null

        if (level != null) {
            drainTracker.add(DrainTracker.Sample(nowMs, level, plugged))
        }

        return BatterySnapshot(
            timestampMs = nowMs,
            levelPercent = level,
            status = status,
            plug = plug,
            voltageMv = voltageMv,
            currentMa = currentMa,
            powerW = powerW,
            temperatureC = temperatureC,
            health = health,
            cycleCount = cycleCount,
            cycleCountApiSupported = cycleSupported,
            technology = technology,
            chargeTimeRemainingMs = chargeTimeMs,
            drain = drainTracker.estimate(nowMs),
            currentUnit = normalizer.unit,
            signMode = normalizer.signMode,
        )
    }

    private fun levelPercent(intent: Intent?): Int? {
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        if (level >= 0 && scale > 0) return (level * 100f / scale).toInt().coerceIn(0, 100)
        val prop = runCatching { batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) }.getOrNull()
        return prop?.takeIf { it in 0..100 }
    }

    private fun plugType(value: Int): PlugType = when (value) {
        0 -> PlugType.NONE
        BatteryManager.BATTERY_PLUGGED_AC -> PlugType.AC
        BatteryManager.BATTERY_PLUGGED_USB -> PlugType.USB
        BatteryManager.BATTERY_PLUGGED_WIRELESS -> PlugType.WIRELESS
        BATTERY_PLUGGED_DOCK -> PlugType.DOCK
        -1 -> PlugType.NONE
        else -> PlugType.UNKNOWN
    }

    private companion object {
        /** BatteryManager.BATTERY_PLUGGED_DOCK (API 33); kept as a literal so it works on minSdk 29. */
        const val BATTERY_PLUGGED_DOCK = 8
    }
}
