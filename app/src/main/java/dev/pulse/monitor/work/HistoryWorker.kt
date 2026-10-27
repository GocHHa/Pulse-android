package dev.pulse.monitor.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.pulse.monitor.PulseApp
import dev.pulse.monitor.data.settings.TempUnit
import dev.pulse.monitor.widget.PulseWidget
import java.util.concurrent.TimeUnit

/**
 * Every 15 minutes (Android's minimum for periodic work): log battery %, temperature and
 * charging state, prune data older than 7 days, check alerts, and refresh the widget.
 */
class HistoryWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as PulseApp).container
        return runCatching {
            val snapshot = container.batteryReader.read()
            container.historyRepository.record(snapshot)

            container.alertEngine.check(
                battery = snapshot,
                thermal = container.cpuReader.thermalLevel(),
                storageFreePercent = container.memoryReader.storageFreePercent(),
            )

            val settings = container.settingsRepository.current()
            PulseWidget.push(
                applicationContext,
                snapshot,
                settings.tempUnit == TempUnit.FAHRENHEIT,
                settings.heatThresholdC,
            )
        }.fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })
    }

    companion object {
        private const val UNIQUE_NAME = "pulse-history-log"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<HistoryWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
