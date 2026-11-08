package com.moobehgames.snoresafe.core.session

import com.moobehgames.snoresafe.core.analysis.SnoreBand
import kotlin.math.roundToInt

/**
 * Snore Score: band-weighted snoring minutes per hour monitored.
 *
 * light = 1, loud = 2, epic = 3. One hour of non-stop light snoring scores 60;
 * non-stop epic snoring scores 180. Normalising by monitored time makes short
 * and long nights comparable.
 */
object SnoreScore {
    private const val MIN_MONITORED_MS = 60_000L

    fun compute(bandMs: Map<SnoreBand, Long>, monitoredMs: Long): Int {
        if (monitoredMs < MIN_MONITORED_MS) return 0
        val weightedMs = SnoreBand.entries.sumOf { (bandMs[it] ?: 0L) * it.weight }
        val weightedMinutes = weightedMs / 60_000.0
        val hours = monitoredMs / 3_600_000.0
        return (weightedMinutes / hours).roundToInt()
    }
}
