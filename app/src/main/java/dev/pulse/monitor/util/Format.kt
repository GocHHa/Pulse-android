package dev.pulse.monitor.util

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Small, dependency-free formatting helpers shared by the UI, notifications and widget. */
object Format {

    fun celsiusToFahrenheit(c: Float): Float = c * 9f / 5f + 32f

    /** Converts a °C value into the display unit. */
    fun tempValue(celsius: Float, fahrenheit: Boolean): Float =
        if (fahrenheit) celsiusToFahrenheit(celsius) else celsius

    fun tempUnit(fahrenheit: Boolean): String = if (fahrenheit) "°F" else "°C"

    fun temp(celsius: Float?, fahrenheit: Boolean, decimals: Int = 1): String {
        celsius ?: return "—"
        return "${number(tempValue(celsius, fahrenheit), decimals)}${tempUnit(fahrenheit)}"
    }

    fun number(value: Float, decimals: Int): String =
        if (decimals <= 0) value.roundToInt().toString()
        else String.format(Locale.US, "%.${decimals}f", value)

    /** "+23.4 W", "−1.8 W". Uses a real minus sign so digits stay aligned. */
    fun signedWatts(watts: Float?): String {
        watts ?: return "—"
        val sign = when {
            watts > 0.05f -> "+"
            watts < -0.05f -> "−"
            else -> ""
        }
        return "$sign${number(abs(watts), 1)} W"
    }

    fun signedMilliamps(ma: Float?): String {
        ma ?: return "—"
        val sign = when {
            ma > 0.5f -> "+"
            ma < -0.5f -> "−"
            else -> ""
        }
        return "$sign${abs(ma).roundToInt()} mA"
    }

    fun volts(mv: Int?): String = mv?.let { "${number(it / 1000f, 2)} V" } ?: "—"

    /** "1 h 23 min", "45 min", "under 1 min". */
    fun duration(ms: Long?): String {
        ms ?: return "—"
        if (ms < 60_000) return "under 1 min"
        val totalMin = (ms / 60_000.0).roundToLong()
        val h = totalMin / 60
        val m = totalMin % 60
        return when {
            h >= 24 -> "${h / 24} d ${h % 24} h"
            h > 0 -> "$h h ${m} min"
            else -> "$m min"
        }
    }

    /** Storage style (decimal, like the phone's own Settings): "412.3 GB". */
    fun bytesDecimal(bytes: Long?): String {
        bytes ?: return "—"
        val b = bytes.toDouble()
        return when {
            b >= 1e12 -> "${String.format(Locale.US, "%.2f", b / 1e12)} TB"
            b >= 1e9 -> "${String.format(Locale.US, "%.1f", b / 1e9)} GB"
            b >= 1e6 -> "${String.format(Locale.US, "%.0f", b / 1e6)} MB"
            else -> "${String.format(Locale.US, "%.0f", b / 1e3)} KB"
        }
    }

    /** RAM style (binary): "11.4 GB". */
    fun bytesBinary(bytes: Long?): String {
        bytes ?: return "—"
        val b = bytes.toDouble()
        val gib = 1024.0 * 1024 * 1024
        val mib = 1024.0 * 1024
        return if (b >= gib) "${String.format(Locale.US, "%.1f", b / gib)} GB"
        else "${String.format(Locale.US, "%.0f", b / mib)} MB"
    }

    data class Speed(val value: Float, val unit: String)

    /** Network speed in bits per second, like a speed test: 12.4 Mbps. */
    fun speed(bytesPerSecond: Double?): Speed? {
        bytesPerSecond ?: return null
        val bits = bytesPerSecond * 8
        return when {
            bits >= 1e9 -> Speed((bits / 1e9).toFloat(), "Gbps")
            bits >= 1e6 -> Speed((bits / 1e6).toFloat(), "Mbps")
            else -> Speed((bits / 1e3).toFloat(), "kbps")
        }
    }

    fun speedText(bytesPerSecond: Double?): String {
        val s = speed(bytesPerSecond) ?: return "—"
        val decimals = if (s.value >= 100f || s.unit == "kbps") 0 else 1
        return "${number(s.value, decimals)} ${s.unit}"
    }

    fun frequency(khz: Long?): String {
        khz ?: return "—"
        return if (khz >= 1_000_000) "${number(khz / 1_000_000f, 2)} GHz" else "${(khz / 1000f).roundToInt()} MHz"
    }
}
