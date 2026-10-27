package dev.pulse.monitor

import android.app.Application
import android.content.Context
import dev.pulse.monitor.alerts.AlertEngine
import dev.pulse.monitor.alerts.Notifications
import dev.pulse.monitor.data.MonitorRepository
import dev.pulse.monitor.data.battery.BatteryReader
import dev.pulse.monitor.data.battery.CurrentNormalizer
import dev.pulse.monitor.data.battery.DrainTracker
import dev.pulse.monitor.data.history.HistoryRepository
import dev.pulse.monitor.data.history.PulseDatabase
import dev.pulse.monitor.data.network.NetworkReader
import dev.pulse.monitor.data.settings.SettingsRepository
import dev.pulse.monitor.data.system.CpuReader
import dev.pulse.monitor.data.system.MemoryReader
import dev.pulse.monitor.work.HistoryWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PulseApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.createChannels(this)
        HistoryWorker.schedule(this)
        container.warmUp()
    }
}

/**
 * Manual dependency injection: one place that builds every long-lived object once.
 * Screens, the worker, the service and the widget all get their dependencies from here.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val currentNormalizer = CurrentNormalizer()
    private val drainTracker = DrainTracker()

    val batteryReader = BatteryReader(appContext, currentNormalizer, drainTracker)
    val cpuReader = CpuReader(appContext)
    val memoryReader = MemoryReader(appContext)
    private val networkReader = NetworkReader(appContext)

    private val database by lazy { PulseDatabase.create(appContext) }
    val historyRepository by lazy { HistoryRepository(database.historyDao()) }
    val settingsRepository = SettingsRepository(appContext)
    val alertEngine = AlertEngine(appContext, settingsRepository)

    val monitorRepository = MonitorRepository(batteryReader, cpuReader, memoryReader, networkReader, appScope)

    /**
     * On launch: seed the drain estimator with the last 3 hours of history so a %/h figure
     * can appear right away, and log a point now if the last one is older than 10 minutes.
     */
    fun warmUp() {
        appScope.launch {
            runCatching {
                val now = System.currentTimeMillis()
                val recent = historyRepository.since(now - 3 * DrainTracker.HOUR_MS)
                drainTracker.seed(recent.map { DrainTracker.Sample(it.timestamp, it.level, it.plugged) })
                val latest = historyRepository.latestTimestamp() ?: 0L
                if (now - latest > 10 * DrainTracker.MINUTE_MS) {
                    historyRepository.record(batteryReader.read(now))
                }
            }
        }
    }
}
