package com.moobehgames.snoresafe.core.trends

import com.moobehgames.snoresafe.core.analysis.SnoreBand
import com.moobehgames.snoresafe.core.session.SessionSummary
import kotlin.math.floor

enum class TrendPeriod(val days: Int?) { WEEK(7), MONTH(30), QUARTER(90), YEAR(365), ALL(null) }

data class TrendStats(
    val nights: Int,
    val averageScore: Float,
    val averageSnoringPercent: Float,
    val averageLoudOrEpicPercent: Float,
    val averageTimeInBedMs: Long,
    val lowestScore: Int,
    val highestScore: Int,
)

data class TrendComparison(val current: TrendStats?, val previous: TrendStats?) {
    /** Negative = less snoring than the previous period. */
    val scoreChange: Float? get() = if (current != null && previous != null) current.averageScore - previous.averageScore else null
}

/** One point per night for charts. */
data class NightPoint(val nightDay: Long, val score: Int, val snoringPercent: Float, val sessionId: String)

object Trends {
    private const val DAY_MS = 24L * 60 * 60 * 1000
    private const val NIGHT_SHIFT_MS = 12L * 60 * 60 * 1000

    /**
     * The "night of" a session, as days since the epoch in local time. Anything started
     * before noon belongs to the previous evening, so 01:00 on Tuesday is Monday night.
     */
    fun nightDay(startEpochMs: Long, utcOffsetMs: Long): Long =
        floor((startEpochMs + utcOffsetMs - NIGHT_SHIFT_MS).toDouble() / DAY_MS).toLong()

    fun stats(sessions: List<SessionSummary>): TrendStats? {
        val nights = sessions.filter { it.monitoredMs > 0 }
        if (nights.isEmpty()) return null
        return TrendStats(
            nights = nights.size,
            averageScore = nights.map { it.snoreScore.toFloat() }.average().toFloat(),
            averageSnoringPercent = nights.map { it.snoringPercent }.average().toFloat(),
            averageLoudOrEpicPercent = nights.map { it.bandPercent(SnoreBand.LOUD) + it.bandPercent(SnoreBand.EPIC) }.average().toFloat(),
            averageTimeInBedMs = nights.map { it.timeInBedMs }.average().toLong(),
            lowestScore = nights.minOf { it.snoreScore },
            highestScore = nights.maxOf { it.snoreScore },
        )
    }

    /** Current period ending at [todayNightDay] (inclusive), compared with the period just before it. */
    fun compare(sessions: List<SessionSummary>, period: TrendPeriod, todayNightDay: Long, utcOffsetMs: Long): TrendComparison {
        val days = period.days ?: return TrendComparison(stats(sessions), null)
        val currentFrom = todayNightDay - days + 1
        val previousFrom = currentFrom - days
        val byDay = sessions.groupBy { nightDay(it.startEpochMs, utcOffsetMs) }
        val current = byDay.filterKeys { it in currentFrom..todayNightDay }.values.flatten()
        val previous = byDay.filterKeys { it in previousFrom until currentFrom }.values.flatten()
        return TrendComparison(stats(current), stats(previous))
    }

    /** One point per night (several sessions in one night are combined by monitored time). */
    fun nightPoints(sessions: List<SessionSummary>, utcOffsetMs: Long, fromNightDay: Long? = null): List<NightPoint> =
        sessions.filter { it.monitoredMs > 0 }
            .groupBy { nightDay(it.startEpochMs, utcOffsetMs) }
            .filterKeys { fromNightDay == null || it >= fromNightDay }
            .map { (day, list) ->
                val monitored = list.sumOf { it.monitoredMs }.toDouble()
                val score = list.sumOf { it.snoreScore * it.monitoredMs.toDouble() } / monitored
                val snoring = list.sumOf { it.snoringMs } * 100.0 / monitored
                NightPoint(day, kotlin.math.round(score).toInt(), snoring.toFloat(), list.maxBy { it.monitoredMs }.id)
            }
            .sortedBy { it.nightDay }
}
