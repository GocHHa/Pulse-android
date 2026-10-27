package dev.pulse.monitor.data.battery

import kotlin.math.abs

/**
 * Turns the raw BATTERY_PROPERTY_CURRENT_NOW value into a trustworthy, signed milliamp figure.
 *
 * The Android docs say the value is in microamps, positive while charging. In practice:
 *  - Many Samsung builds report **milliamps** instead of microamps.
 *  - Some builds use the **opposite sign**, and some report a **positive magnitude no matter what**.
 *
 * How we decide, intuitively:
 *  - **Unit**: a phone never pulls 20 A. So if we ever see |raw| ≥ 20 000 it must be µA.
 *    Below that we assume mA (a µA device would show ≥ 20 000 within the first second,
 *    because a phone with its screen on draws far more than 20 mA).
 *  - **Sign**: when the phone is unplugged, current *must* flow out of the battery. So any
 *    reading taken while unplugged tells us what "discharging" looks like on this device.
 *    Readings taken while the system says "charging" tell us what "charging" looks like.
 *    If those two look different (opposite signs), the sensor is signed and we can trust it,
 *    even to spot a net drain while plugged in under heavy load. If they look the same, the
 *    sensor only gives a magnitude, so we take the direction from the charging status.
 *
 * Result convention: **positive = energy flowing into the battery**, negative = out of it.
 */
class CurrentNormalizer {

    enum class CurrentUnit { MICROAMPS, MILLIAMPS }

    enum class SignMode {
        /** Not enough evidence yet; direction comes from charging status. */
        LEARNING,
        /** Sensor follows the Android convention (+ while charging). */
        STANDARD,
        /** Sensor reports the opposite sign (− while charging). */
        INVERTED,
        /** Sensor reports the same sign either way; direction comes from charging status. */
        MAGNITUDE_ONLY,
    }

    private var maxAbsSeen: Long = 0
    private var seenNonZero = false
    private var dischargePositive = 0
    private var dischargeNegative = 0
    private var chargePositive = 0
    private var chargeNegative = 0

    @get:Synchronized
    val unit: CurrentUnit?
        get() = if (!seenNonZero) null else if (maxAbsSeen >= MICROAMP_THRESHOLD) CurrentUnit.MICROAMPS else CurrentUnit.MILLIAMPS

    @get:Synchronized
    val signMode: SignMode
        get() {
            val dischargeSign = majority(dischargePositive, dischargeNegative)
            val chargeSign = majority(chargePositive, chargeNegative)
            return when {
                dischargeSign == 0 || chargeSign == 0 -> SignMode.LEARNING
                dischargeSign == chargeSign -> SignMode.MAGNITUDE_ONLY
                chargeSign > 0 -> SignMode.STANDARD
                else -> SignMode.INVERTED
            }
        }

    /**
     * @param raw value from BatteryManager, or null if the call failed.
     * @param plugged whether any power source is connected.
     * @param charging whether the system status is BATTERY_STATUS_CHARGING.
     * @return signed current in mA (+ into battery), or null if the device does not report it.
     */
    @Synchronized
    fun normalize(raw: Int?, plugged: Boolean, charging: Boolean): Float? {
        if (raw == null || raw == Int.MIN_VALUE || raw == Int.MAX_VALUE) return null
        if (raw == 0) {
            // Some devices return 0 forever when the property is unsupported.
            return if (seenNonZero) 0f else null
        }
        seenNonZero = true
        val absRaw = abs(raw.toLong())
        if (absRaw > maxAbsSeen) maxAbsSeen = absRaw

        val magnitudeMa = if (maxAbsSeen >= MICROAMP_THRESHOLD) absRaw / 1000f else absRaw.toFloat()
        val rawSign = if (raw > 0) 1 else -1

        // Collect evidence (capped so the counters adapt if firmware behaviour changes).
        if (!plugged) {
            if (rawSign > 0) dischargePositive++ else dischargeNegative++
        } else if (charging && magnitudeMa >= MIN_EVIDENCE_MA) {
            if (rawSign > 0) chargePositive++ else chargeNegative++
        }
        capCounters()

        return when (signMode) {
            SignMode.STANDARD -> magnitudeMa * rawSign
            SignMode.INVERTED -> -magnitudeMa * rawSign
            SignMode.MAGNITUDE_ONLY, SignMode.LEARNING -> when {
                !plugged -> -magnitudeMa
                charging -> magnitudeMa
                // Plugged but not charging (full / battery protection): tiny current either way.
                // If we already know how discharge looks, use that; otherwise Android convention.
                else -> {
                    val dischargeSign = majority(dischargePositive, dischargeNegative)
                    if (dischargeSign != 0 && rawSign == dischargeSign) -magnitudeMa else magnitudeMa * rawSign
                }
            }
        }
    }

    private fun majority(positive: Int, negative: Int): Int = when {
        positive + negative < MIN_SAMPLES -> 0
        positive > negative * 3 -> 1
        negative > positive * 3 -> -1
        else -> 0
    }

    private fun capCounters() {
        if (dischargePositive + dischargeNegative > MAX_SAMPLES) {
            dischargePositive /= 2; dischargeNegative /= 2
        }
        if (chargePositive + chargeNegative > MAX_SAMPLES) {
            chargePositive /= 2; chargeNegative /= 2
        }
    }

    companion object {
        /** 20 000 mA would be 20 A — impossible for a phone, so values this large are µA. */
        const val MICROAMP_THRESHOLD = 20_000L
        private const val MIN_EVIDENCE_MA = 50f
        private const val MIN_SAMPLES = 3
        private const val MAX_SAMPLES = 600
    }
}
