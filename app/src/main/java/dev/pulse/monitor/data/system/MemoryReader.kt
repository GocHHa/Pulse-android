package dev.pulse.monitor.data.system

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import dev.pulse.monitor.data.model.MemorySnapshot

/** RAM through ActivityManager.MemoryInfo and internal storage through StatFs. */
class MemoryReader(context: Context) {

    private val activityManager =
        context.applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    fun read(): MemorySnapshot {
        val info = ActivityManager.MemoryInfo()
        val ramOk = runCatching { activityManager.getMemoryInfo(info) }.isSuccess && info.totalMem > 0

        val advertised = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && ramOk) {
            info.advertisedMem.takeIf { it > 0 }
        } else null

        val stat = runCatching { StatFs(Environment.getDataDirectory().absolutePath) }.getOrNull()
        val total = stat?.totalBytes?.takeIf { it > 0 }
        val available = stat?.availableBytes?.takeIf { it >= 0 }

        return MemorySnapshot(
            totalRamBytes = if (ramOk) info.totalMem else null,
            availableRamBytes = if (ramOk) info.availMem else null,
            lowMemoryThresholdBytes = if (ramOk) info.threshold.takeIf { it > 0 } else null,
            isLowMemory = ramOk && info.lowMemory,
            advertisedRamBytes = advertised,
            storageTotalBytes = total,
            storageAvailableBytes = if (total != null) available else null,
        )
    }

    /** Free storage as a percentage, for background alerts. */
    fun storageFreePercent(): Float? = read().storageFreePercent
}
