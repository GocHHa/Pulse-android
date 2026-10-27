package dev.pulse.monitor.data.battery

/**
 * Estimates how fast the battery is draining (% per hour) from recent level readings.
 *
 * Think of it like a car's "average consumption" display: we look at where the battery
 * level was when you unplugged (or a few hours ago, whichever is later), compare it with
 * now, and divide by the time that passed. Battery level only moves in whole percents, so
 * we wait until it has dropped at least 1% (and a few minutes have passed) before showing
 * a number. Until then the estimate is honestly reported as "measuring".
 */
class DrainTracker(
    private val windowMs: Long = 3 * HOUR_MS,
    private val minSpanMs: Long = 5 * MINUTE_MS,
) {
    data class Sample(val timeMs: Long, val level: Int, val plugged: Boolean)

    sealed interface Estimate {
        /** Phone is plugged in, so a drain rate doesn't apply. */
        data object NotDischarging : Estimate

        /** Not enough data yet (needs at least a 1% drop over a few minutes). */
        data class Measuring(val sinceMs: Long?) : Estimate

        /** Level hasn't dropped at all for a while: rate is below [maxPercentPerHour]. */
        data class Below(val maxPercentPerHour: Float, val spanMs: Long) : Estimate

        data class Rate(val percentPerHour: Float, val spanMs: Long) : Estimate
    }

    private val samples = ArrayDeque<Sample>()

    @Synchronized
    fun add(sample: Sample) {
        val last = samples.lastOrNull()
        if (last != null) {
            if (sample.timeMs < last.timeMs) return
            val changed = last.level != sample.level || last.plugged != sample.plugged
            // Keep the list small: store a new point when something changed or once a minute.
            if (!changed && sample.timeMs - last.timeMs < MINUTE_MS) return
        }
        samples.addLast(sample)
        trim(sample.timeMs)
    }

    /** Seeds older readings (e.g. from the history database) so estimates appear sooner. */
    @Synchronized
    fun seed(older: List<Sample>) {
        if (older.isEmpty()) return
        val merged = (older + samples).sortedBy { it.timeMs }
        samples.clear()
        merged.forEach { s ->
            val last = samples.lastOrNull()
            if (last == null || s.timeMs > last.timeMs) samples.addLast(s)
        }
        trim(samples.last().timeMs)
    }

    @Synchronized
    fun estimate(nowMs: Long): Estimate {
        val latest = samples.lastOrNull() ?: return Estimate.Measuring(null)
        if (latest.plugged) return Estimate.NotDischarging

        // Walk backwards to the start of the current unplugged stretch, inside the window.
        var start = latest
        for (i in samples.indices.reversed()) {
            val s = samples[i]
            if (s.plugged) break
            if (latest.timeMs - s.timeMs > windowMs) break
            start = s
        }
        val spanMs = nowMs - start.timeMs
        val drop = start.level - latest.level
        val hours = spanMs / HOUR_MS.toFloat()

        return when {
            drop >= 1 && spanMs >= minSpanMs -> Estimate.Rate(drop / hours, spanMs)
            drop <= 0 && spanMs >= 20 * MINUTE_MS -> Estimate.Below(1f / hours, spanMs)
            else -> Estimate.Measuring(start.timeMs)
        }
    }

    private fun trim(nowMs: Long) {
        while (samples.size > 1 && nowMs - samples.first().timeMs > windowMs + HOUR_MS) {
            samples.removeFirst()
        }
        while (samples.size > MAX_SAMPLES) samples.removeFirst()
    }

    companion object {
        const val MINUTE_MS = 60_000L
        const val HOUR_MS = 60 * MINUTE_MS
        private const val MAX_SAMPLES = 1_000
    }
}
