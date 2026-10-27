package dev.pulse.monitor.data.system

import android.content.Context
import android.os.Build
import android.os.PowerManager
import dev.pulse.monitor.data.model.CoreInfo
import dev.pulse.monitor.data.model.CpuSnapshot
import dev.pulse.monitor.data.model.ThermalLevel
import java.io.File

/**
 * Per-core clock speeds from sysfs plus Android's thermal APIs.
 *
 * Note: Android no longer lets regular apps read system-wide CPU *usage* (/proc/stat is
 * blocked), so Pulse shows clock speed instead. Intuitively: a core running near its
 * maximum clock is working hard; a core idling at its minimum clock is resting.
 */
class CpuReader(context: Context) {

    private val powerManager = context.applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val coreCount: Int = detectCoreCount()
    private val minFreq = LongArray(coreCount) { -1L }
    private val maxFreq = LongArray(coreCount) { -1L }

    private var lastHeadroom: Float? = null
    private var lastHeadroomAtMs = 0L
    private var headroomAttempts = 0
    private var headroomEverValid = false

    fun read(nowMs: Long = System.currentTimeMillis()): CpuSnapshot {
        val cores = (0 until coreCount).map { readCore(it) }
        return CpuSnapshot(
            cores = cores,
            frequencyAvailable = cores.any { it.currentKhz != null },
            thermal = runCatching { ThermalLevel.fromCode(powerManager.currentThermalStatus) }.getOrNull(),
            thermalHeadroom = readHeadroom(nowMs),
            socName = socName(),
        )
    }

    /** Reads only the thermal status (used by background alerts). */
    fun thermalLevel(): ThermalLevel? =
        runCatching { ThermalLevel.fromCode(powerManager.currentThermalStatus) }.getOrNull()

    private fun readCore(i: Int): CoreInfo {
        val base = "/sys/devices/system/cpu/cpu$i"
        val onlineFile = File("$base/online")
        // cpu0 usually has no "online" file because it can't be turned off.
        val online = if (onlineFile.exists()) readLong(onlineFile) != 0L else true

        if (maxFreq[i] <= 0) maxFreq[i] = readLong(File("$base/cpufreq/cpuinfo_max_freq")) ?: -1L
        if (minFreq[i] <= 0) minFreq[i] = readLong(File("$base/cpufreq/cpuinfo_min_freq")) ?: -1L

        val current = if (online) {
            readLong(File("$base/cpufreq/scaling_cur_freq"))
                ?: readLong(File("$base/cpufreq/cpuinfo_cur_freq"))
                ?: readPolicyFreq(i)
        } else null

        return CoreInfo(
            index = i,
            online = online,
            currentKhz = current?.takeIf { it > 0 },
            minKhz = minFreq[i].takeIf { it > 0 },
            maxKhz = maxFreq[i].takeIf { it > 0 },
        )
    }

    /** Fallback: some kernels only expose frequencies per policy (cluster). */
    private fun readPolicyFreq(core: Int): Long? {
        val dir = File("/sys/devices/system/cpu/cpufreq")
        val policies = runCatching { dir.listFiles { f -> f.name.startsWith("policy") } }.getOrNull() ?: return null
        for (policy in policies) {
            val related = runCatching { File(policy, "related_cpus").readText().trim() }.getOrNull() ?: continue
            val cpus = related.split(' ').mapNotNull { it.trim().toIntOrNull() }
            if (core in cpus) return readLong(File(policy, "scaling_cur_freq"))
        }
        return null
    }

    private fun readHeadroom(nowMs: Long): Float? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        if (!headroomEverValid && headroomAttempts > 8) return null
        // The API returns NaN if called more than about once per second, so cache.
        if (nowMs - lastHeadroomAtMs < 1_000 && lastHeadroom != null) return lastHeadroom
        headroomAttempts++
        val value = runCatching { powerManager.getThermalHeadroom(10) }.getOrNull()
        if (value != null && !value.isNaN() && value >= 0f) {
            headroomEverValid = true
            lastHeadroom = value
            lastHeadroomAtMs = nowMs
            return value
        }
        // Keep showing the last good value for a few seconds instead of flickering.
        return if (nowMs - lastHeadroomAtMs < 5_000) lastHeadroom else null
    }

    private fun socName(): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return Build.HARDWARE.takeIf { it.isNotBlank() }
        val model = Build.SOC_MODEL.takeIf { it.isNotBlank() && it != Build.UNKNOWN }
        val maker = Build.SOC_MANUFACTURER.takeIf { it.isNotBlank() && it != Build.UNKNOWN }
        return listOfNotNull(maker, model).joinToString(" ").ifBlank { null }
    }

    private fun detectCoreCount(): Int {
        val fromSysfs = runCatching {
            File("/sys/devices/system/cpu").listFiles { f -> f.name.matches(Regex("cpu\\d+")) }?.size
        }.getOrNull() ?: 0
        return maxOf(fromSysfs, Runtime.getRuntime().availableProcessors(), 1)
    }

    private fun readLong(file: File): Long? = runCatching {
        file.readText().trim().toLong()
    }.getOrNull()
}
