package dev.pulse.monitor.data.model

import dev.pulse.monitor.data.battery.CurrentNormalizer
import dev.pulse.monitor.data.battery.DrainTracker

/*
 * Snapshot models. A `null` field always means "this device does not report it",
 * which the UI renders as "Not available on this device". Nothing here is ever faked.
 */

enum class ChargeStatus { CHARGING, DISCHARGING, FULL, NOT_CHARGING, UNKNOWN }

enum class PlugType { NONE, AC, USB, WIRELESS, DOCK, UNKNOWN }

enum class BatteryHealth { GOOD, OVERHEAT, DEAD, OVER_VOLTAGE, FAILURE, COLD, UNKNOWN }

data class BatterySnapshot(
    val timestampMs: Long,
    val levelPercent: Int?,
    val status: ChargeStatus,
    val plug: PlugType,
    val voltageMv: Int?,
    /** Signed current in mA, + = into the battery. */
    val currentMa: Float?,
    /** Signed power in watts, + = into the battery. */
    val powerW: Float?,
    val temperatureC: Float?,
    val health: BatteryHealth,
    /** Null when the device doesn't report it (or Android < 14). */
    val cycleCount: Int?,
    val cycleCountApiSupported: Boolean,
    val technology: String?,
    val chargeTimeRemainingMs: Long?,
    val drain: DrainTracker.Estimate,
    val currentUnit: CurrentNormalizer.CurrentUnit?,
    val signMode: CurrentNormalizer.SignMode,
) {
    val isPlugged: Boolean get() = plug != PlugType.NONE
    val isCharging: Boolean get() = status == ChargeStatus.CHARGING

    /** Hours left on battery at the current drain rate. */
    val timeLeftOnBatteryMs: Long?
        get() {
            val rate = (drain as? DrainTracker.Estimate.Rate)?.percentPerHour ?: return null
            val level = levelPercent ?: return null
            if (rate <= 0f) return null
            return (level / rate * DrainTracker.HOUR_MS).toLong()
        }
}

data class CoreInfo(
    val index: Int,
    val online: Boolean,
    val currentKhz: Long?,
    val minKhz: Long?,
    val maxKhz: Long?,
) {
    /** 0..1 position between this core's min and max clock, or null if unknown. */
    val load: Float?
        get() {
            val cur = currentKhz ?: return null
            val max = maxKhz ?: return null
            if (max <= 0) return null
            return (cur.toFloat() / max).coerceIn(0f, 1f)
        }
}

enum class ThermalLevel(val code: Int) {
    NONE(0), LIGHT(1), MODERATE(2), SEVERE(3), CRITICAL(4), EMERGENCY(5), SHUTDOWN(6);

    companion object {
        fun fromCode(code: Int): ThermalLevel = entries.firstOrNull { it.code == code } ?: NONE
    }
}

data class CpuSnapshot(
    val cores: List<CoreInfo>,
    /** True when at least one core's live frequency could be read. */
    val frequencyAvailable: Boolean,
    val thermal: ThermalLevel?,
    /** 0 = cool, 1.0 = at the throttling point (can exceed 1). Null if unsupported. */
    val thermalHeadroom: Float?,
    val socName: String?,
)

data class MemorySnapshot(
    val totalRamBytes: Long?,
    val availableRamBytes: Long?,
    val lowMemoryThresholdBytes: Long?,
    val isLowMemory: Boolean,
    val advertisedRamBytes: Long?,
    val storageTotalBytes: Long?,
    val storageAvailableBytes: Long?,
) {
    val usedRamBytes: Long?
        get() = if (totalRamBytes != null && availableRamBytes != null) totalRamBytes - availableRamBytes else null

    val ramUsedFraction: Float?
        get() = if (totalRamBytes != null && totalRamBytes > 0 && usedRamBytes != null) {
            usedRamBytes!!.toFloat() / totalRamBytes
        } else null

    val storageUsedBytes: Long?
        get() = if (storageTotalBytes != null && storageAvailableBytes != null) storageTotalBytes - storageAvailableBytes else null

    val storageUsedFraction: Float?
        get() = if (storageTotalBytes != null && storageTotalBytes > 0 && storageUsedBytes != null) {
            storageUsedBytes!!.toFloat() / storageTotalBytes
        } else null

    val storageFreePercent: Float?
        get() = storageUsedFraction?.let { (1f - it) * 100f }
}

enum class Transport { WIFI, CELLULAR, ETHERNET, VPN, OTHER, NONE }

data class WifiDetails(
    val rssiDbm: Int?,
    /** 0 (worst) to 4 (best). */
    val signalLevel: Int?,
    val linkSpeedMbps: Int?,
    val txLinkSpeedMbps: Int?,
    val rxLinkSpeedMbps: Int?,
    val frequencyMhz: Int?,
    val band: String?,
    val standard: String?,
)

data class CellDetails(
    /** 0 (none) to 4 (best). */
    val signalLevel: Int?,
    val dbm: Int?,
    val technology: String?,
    val operatorName: String?,
)

data class NetworkSnapshot(
    val timestampMs: Long,
    /** Bytes per second. Null while measuring the first interval or if unsupported. */
    val downloadBps: Double?,
    val uploadBps: Double?,
    val trafficStatsSupported: Boolean,
    val transport: Transport,
    val internetValidated: Boolean,
    val metered: Boolean,
    val wifi: WifiDetails?,
    val cell: CellDetails?,
    val hasTelephony: Boolean,
)
