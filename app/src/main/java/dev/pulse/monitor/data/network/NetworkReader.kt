package dev.pulse.monitor.data.network

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.telephony.CellInfo
import android.telephony.CellSignalStrength
import android.telephony.CellSignalStrengthCdma
import android.telephony.CellSignalStrengthGsm
import android.telephony.CellSignalStrengthLte
import android.telephony.CellSignalStrengthNr
import android.telephony.CellSignalStrengthTdscdma
import android.telephony.CellSignalStrengthWcdma
import android.telephony.TelephonyManager
import dev.pulse.monitor.data.model.CellDetails
import dev.pulse.monitor.data.model.NetworkSnapshot
import dev.pulse.monitor.data.model.Transport
import dev.pulse.monitor.data.model.WifiDetails

/**
 * Live throughput from TrafficStats byte counters plus Wi-Fi / mobile signal details.
 *
 * Speed works like a car's trip computer: we read the total bytes moved since boot, wait a
 * second, read again, and the difference is the speed. Nothing here needs location
 * permission, so the Wi-Fi network *name* is intentionally not shown.
 */
class NetworkReader(context: Context) {

    private val appContext = context.applicationContext
    private val connectivity = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val telephony = appContext.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
    private val hasTelephony = appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)

    private var lastRx = -1L
    private var lastTx = -1L
    private var lastTimeNs = 0L

    /** Call roughly once per second from a single coroutine. */
    fun read(nowMs: Long = System.currentTimeMillis()): NetworkSnapshot {
        val rx = TrafficStats.getTotalRxBytes()
        val tx = TrafficStats.getTotalTxBytes()
        val supported = rx != TrafficStats.UNSUPPORTED.toLong() && tx != TrafficStats.UNSUPPORTED.toLong()
        val nowNs = System.nanoTime()

        var down: Double? = null
        var up: Double? = null
        if (supported && lastRx >= 0 && lastTx >= 0 && lastTimeNs > 0) {
            val seconds = (nowNs - lastTimeNs) / 1_000_000_000.0
            if (seconds > 0.2) {
                // Counters can reset (e.g. interface restart); never report negative speed.
                down = ((rx - lastRx).coerceAtLeast(0L)) / seconds
                up = ((tx - lastTx).coerceAtLeast(0L)) / seconds
            }
        }
        if (supported) {
            lastRx = rx; lastTx = tx; lastTimeNs = nowNs
        }

        val caps = runCatching { connectivity.getNetworkCapabilities(connectivity.activeNetwork) }.getOrNull()
        val transport = when {
            caps == null -> Transport.NONE
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> Transport.VPN
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> Transport.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> Transport.CELLULAR
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> Transport.ETHERNET
            else -> Transport.OTHER
        }
        val onWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

        return NetworkSnapshot(
            timestampMs = nowMs,
            downloadBps = down,
            uploadBps = up,
            trafficStatsSupported = supported,
            transport = transport,
            internetValidated = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true,
            metered = caps != null && !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
            wifi = if (onWifi) readWifi(caps) else null,
            cell = if (hasTelephony) readCell() else null,
            hasTelephony = hasTelephony,
        )
    }

    private fun readWifi(caps: NetworkCapabilities?): WifiDetails? {
        val info: WifiInfo? = wifiInfoFrom(caps) ?: legacyWifiInfo()
        info ?: return null

        val rssi = info.rssi.takeIf { it in -126..0 }
        val level = rssi?.let { r ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val max = wifiManager?.maxSignalLevel ?: 4
                val raw = wifiManager?.calculateSignalLevel(r) ?: return@let null
                if (max <= 0) null else (raw * 4f / max).toInt().coerceIn(0, 4)
            } else {
                @Suppress("DEPRECATION")
                WifiManager.calculateSignalLevel(r, 5)
            }
        }
        val freq = info.frequency.takeIf { it > 0 }
        return WifiDetails(
            rssiDbm = rssi,
            signalLevel = level,
            linkSpeedMbps = info.linkSpeed.takeIf { it > 0 },
            txLinkSpeedMbps = info.txLinkSpeedMbps.takeIf { it > 0 },
            rxLinkSpeedMbps = info.rxLinkSpeedMbps.takeIf { it > 0 },
            frequencyMhz = freq,
            band = freq?.let(::bandFor),
            standard = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) standardName(info.wifiStandard, freq) else null,
        )
    }

    private fun wifiInfoFrom(caps: NetworkCapabilities?): WifiInfo? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        return caps?.transportInfo as? WifiInfo
    }

    @Suppress("DEPRECATION")
    private fun legacyWifiInfo(): WifiInfo? = runCatching { wifiManager?.connectionInfo }.getOrNull()

    private fun readCell(): CellDetails? {
        val tm = telephony ?: return null
        val strength = runCatching { tm.signalStrength }.getOrNull() ?: return null
        val list: List<CellSignalStrength> = runCatching { strength.cellSignalStrengths }.getOrNull().orEmpty()
        if (list.isEmpty()) return null

        val primary = list.firstOrNull { it is CellSignalStrengthNr }
            ?: list.firstOrNull { it is CellSignalStrengthLte }
            ?: list.first()
        val dbm = primary.dbm.takeIf { it != CellInfo.UNAVAILABLE && it in -160..-20 }

        val hasNr = list.any { it is CellSignalStrengthNr }
        val hasLte = list.any { it is CellSignalStrengthLte }
        val technology = when {
            hasNr && hasLte -> "5G NSA (LTE + NR)"
            hasNr -> "5G NR"
            hasLte -> "4G LTE"
            list.any { it is CellSignalStrengthWcdma || it is CellSignalStrengthTdscdma } -> "3G"
            list.any { it is CellSignalStrengthGsm } -> "2G GSM"
            list.any { it is CellSignalStrengthCdma } -> "CDMA"
            else -> null
        }
        val operator = runCatching { tm.networkOperatorName }.getOrNull()?.takeIf { it.isNotBlank() }

        return CellDetails(
            signalLevel = runCatching { strength.level }.getOrNull()?.coerceIn(0, 4),
            dbm = dbm,
            technology = technology,
            operatorName = operator,
        )
    }

    private fun bandFor(freqMhz: Int): String? = when (freqMhz) {
        in 2400..2500 -> "2.4 GHz"
        in 4900..5899 -> "5 GHz"
        in 5925..7125 -> "6 GHz"
        in 57000..71000 -> "60 GHz"
        else -> null
    }

    private fun standardName(code: Int, freqMhz: Int?): String? = when (code) {
        1 -> "Legacy (802.11a/b/g)"
        4 -> "Wi-Fi 4 (802.11n)"
        5 -> "Wi-Fi 5 (802.11ac)"
        6 -> if (freqMhz != null && freqMhz >= 5925) "Wi-Fi 6E (802.11ax)" else "Wi-Fi 6 (802.11ax)"
        7 -> "WiGig (802.11ad)"
        8 -> "Wi-Fi 7 (802.11be)"
        else -> null
    }
}
