package dev.pulse.monitor.data

import dev.pulse.monitor.data.battery.BatteryReader
import dev.pulse.monitor.data.model.BatterySnapshot
import dev.pulse.monitor.data.model.CpuSnapshot
import dev.pulse.monitor.data.model.MemorySnapshot
import dev.pulse.monitor.data.model.NetworkSnapshot
import dev.pulse.monitor.data.network.NetworkReader
import dev.pulse.monitor.data.system.CpuReader
import dev.pulse.monitor.data.system.MemoryReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.shareIn

/**
 * Live hardware readings as hot flows that tick about once per second.
 *
 * They are "shared while watched": polling starts when the first screen starts collecting
 * and stops a couple of seconds after the last one stops (for example when the app goes to
 * the background), so Pulse does no work while you're not looking at it.
 */
class MonitorRepository(
    private val batteryReader: BatteryReader,
    private val cpuReader: CpuReader,
    private val memoryReader: MemoryReader,
    private val networkReader: NetworkReader,
    scope: CoroutineScope,
) {
    val battery: SharedFlow<BatterySnapshot> = ticker(TICK_MS) { batteryReader.read() }.share(scope)
    val cpu: SharedFlow<CpuSnapshot> = ticker(TICK_MS) { cpuReader.read() }.share(scope)
    val memory: SharedFlow<MemorySnapshot> = ticker(TICK_MS * 2) { memoryReader.read() }.share(scope)
    val network: SharedFlow<NetworkSnapshot> = ticker(TICK_MS) { networkReader.read() }.share(scope)

    private fun <T> ticker(periodMs: Long, read: () -> T): Flow<T> = flow {
        while (true) {
            emit(read())
            delay(periodMs)
        }
    }.flowOn(Dispatchers.IO)

    private fun <T> Flow<T>.share(scope: CoroutineScope): SharedFlow<T> =
        shareIn(scope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 2_000), replay = 1)

    companion object {
        const val TICK_MS = 1_000L
    }
}
