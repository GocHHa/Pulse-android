package dev.pulse.monitor.ui

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.pulse.monitor.AppContainer
import dev.pulse.monitor.data.history.HistoryEntity
import dev.pulse.monitor.data.model.BatterySnapshot
import dev.pulse.monitor.data.model.CpuSnapshot
import dev.pulse.monitor.data.model.MemorySnapshot
import dev.pulse.monitor.data.model.NetworkSnapshot
import dev.pulse.monitor.data.settings.AppSettings
import dev.pulse.monitor.data.settings.TempUnit
import dev.pulse.monitor.data.settings.ThemeMode
import dev.pulse.monitor.service.LiveMonitorService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Screens reach the manually-built dependency graph through this. */
val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("AppContainer not provided")
}

/**
 * Every screen's state is "WhileSubscribed": when the screen leaves the foreground the
 * collection stops, so the 1-second polling upstream pauses automatically.
 */
private fun <T> Flow<T>.asState(vm: ViewModel, initial: T): StateFlow<T> =
    stateIn(vm.viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

/** Keeps the last [size] values, newest last. */
private fun <T, R> Flow<T>.rolling(size: Int, transform: (T) -> R): Flow<List<R>> =
    scan(emptyList<R>()) { acc, value -> (acc + transform(value)).takeLast(size) }

// ---------------------------------------------------------------- Dashboard

data class DashboardUiState(
    val battery: BatterySnapshot? = null,
    val cpu: CpuSnapshot? = null,
    val memory: MemorySnapshot? = null,
    val network: NetworkSnapshot? = null,
    val settings: AppSettings = AppSettings(),
)

class DashboardViewModel(container: AppContainer) : ViewModel() {
    private val monitor = container.monitorRepository

    val state: StateFlow<DashboardUiState> = combine(
        monitor.battery,
        monitor.cpu,
        monitor.memory,
        monitor.network,
        container.settingsRepository.settings,
    ) { battery, cpu, memory, network, settings ->
        DashboardUiState(battery, cpu, memory, network, settings)
    }.asState(this, DashboardUiState())

    /** Last 40 seconds of download speed for the network card sparkline. */
    val downloadHistory: StateFlow<List<Float>> = monitor.network
        .rolling(40) { (it.downloadBps ?: 0.0).toFloat() }
        .asState(this, emptyList())

    /** Last 24 h of logged battery levels for the history teaser card. */
    val historyPreview: StateFlow<List<HistoryEntity>> =
        container.historyRepository.observeSince(System.currentTimeMillis() - 24 * HOUR_MS)
            .asState(this, emptyList())
}

// ---------------------------------------------------------------- Battery

data class BatteryUiState(
    val battery: BatterySnapshot? = null,
    val settings: AppSettings = AppSettings(),
)

class BatteryViewModel(container: AppContainer) : ViewModel() {
    private val monitor = container.monitorRepository

    val state: StateFlow<BatteryUiState> = combine(
        monitor.battery,
        container.settingsRepository.settings,
    ) { battery, settings -> BatteryUiState(battery, settings) }
        .asState(this, BatteryUiState())

    /** Last 90 seconds of battery power (W) for the live power trace. */
    val powerHistory: StateFlow<List<Float?>> = monitor.battery
        .rolling(90) { it.powerW }
        .asState(this, emptyList())
}

// ---------------------------------------------------------------- Performance

data class PerformanceUiState(
    val cpu: CpuSnapshot? = null,
    val batteryTempC: Float? = null,
    val settings: AppSettings = AppSettings(),
)

class PerformanceViewModel(container: AppContainer) : ViewModel() {
    private val monitor = container.monitorRepository

    val state: StateFlow<PerformanceUiState> = combine(
        monitor.cpu,
        monitor.battery,
        container.settingsRepository.settings,
    ) { cpu, battery, settings -> PerformanceUiState(cpu, battery.temperatureC, settings) }
        .asState(this, PerformanceUiState())
}

// ---------------------------------------------------------------- Memory & storage

class MemoryViewModel(container: AppContainer) : ViewModel() {
    val state: StateFlow<MemorySnapshot?> = container.monitorRepository.memory
        .map<MemorySnapshot, MemorySnapshot?> { it }
        .asState(this, null)
}

// ---------------------------------------------------------------- Network

data class SpeedSample(val seq: Long, val downBps: Float, val upBps: Float)

data class NetworkUiState(
    val network: NetworkSnapshot? = null,
    val samples: List<SpeedSample> = emptyList(),
)

class NetworkViewModel(container: AppContainer) : ViewModel() {
    private val monitor = container.monitorRepository

    val state: StateFlow<NetworkUiState> = monitor.network
        .scan(NetworkUiState()) { acc, snapshot ->
            val seq = (acc.samples.lastOrNull()?.seq ?: 0L) + 1
            val sample = SpeedSample(
                seq = seq,
                downBps = (snapshot.downloadBps ?: 0.0).toFloat(),
                upBps = (snapshot.uploadBps ?: 0.0).toFloat(),
            )
            NetworkUiState(
                network = snapshot,
                samples = (acc.samples + sample).takeLast(WINDOW + 2),
            )
        }
        .asState(this, NetworkUiState())

    companion object {
        const val WINDOW = 60
    }
}

// ---------------------------------------------------------------- History

enum class HistoryRange(val label: String, val durationMs: Long) {
    DAY("24 hours", 24 * HOUR_MS),
    WEEK("7 days", 7 * 24 * HOUR_MS),
}

data class HistoryUiState(
    val range: HistoryRange = HistoryRange.DAY,
    val rangeStartMs: Long = 0L,
    val entries: List<HistoryEntity> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val loaded: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(container: AppContainer) : ViewModel() {
    private val range = MutableStateFlow(HistoryRange.DAY)

    val state: StateFlow<HistoryUiState> = combine(
        range.flatMapLatest { r ->
            val start = System.currentTimeMillis() - r.durationMs
            container.historyRepository.observeSince(start).map { Triple(r, start, it) }
        },
        container.settingsRepository.settings,
    ) { (r, start, entries), settings ->
        HistoryUiState(range = r, rangeStartMs = start, entries = entries, settings = settings, loaded = true)
    }.asState(this, HistoryUiState())

    fun setRange(value: HistoryRange) {
        range.value = value
    }
}

// ---------------------------------------------------------------- Settings

class SettingsViewModel(container: AppContainer) : ViewModel() {
    private val repo = container.settingsRepository

    val state: StateFlow<AppSettings?> = repo.settings
        .map<AppSettings, AppSettings?> { it }
        .asState(this, null)

    fun setTheme(mode: ThemeMode) = perform { repo.setThemeMode(mode) }
    fun setTempUnit(unit: TempUnit) = perform { repo.setTempUnit(unit) }
    fun setChargeAlert(enabled: Boolean) = perform { repo.setChargeAlertEnabled(enabled) }
    fun setChargeTarget(percent: Int) = perform { repo.setChargeTarget(percent) }
    fun setHeatAlert(enabled: Boolean) = perform { repo.setHeatAlertEnabled(enabled) }
    fun setHeatThreshold(celsius: Float) = perform { repo.setHeatThreshold(celsius) }
    fun setStorageAlert(enabled: Boolean) = perform { repo.setStorageAlertEnabled(enabled) }
    fun setStorageThreshold(percent: Int) = perform { repo.setStorageThreshold(percent) }

    fun setLiveMonitoring(context: Context, enabled: Boolean) = perform {
        repo.setLiveMonitoring(enabled)
        if (enabled) LiveMonitorService.start(context.applicationContext)
        else LiveMonitorService.stop(context.applicationContext)
    }

    private fun perform(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}

const val HOUR_MS = 60L * 60L * 1000L
