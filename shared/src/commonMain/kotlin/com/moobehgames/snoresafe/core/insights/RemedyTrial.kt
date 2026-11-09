package com.moobehgames.snoresafe.core.insights

import com.moobehgames.snoresafe.core.session.SessionSummary
import com.moobehgames.snoresafe.core.trends.Trends
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.random.Random

/**
 * A fair test of one remedy or factor: nights are shuffled into "with" and "without" in equal numbers,
 * so habits and weekdays don't bias the comparison, then a permutation test says whether the
 * difference is likely real.
 */
@Serializable
data class RemedyTrial(
    val tagId: String,
    val startNightDay: Long,
    /** schedule[i] = use the tag on night startNightDay + i. */
    val schedule: List<Boolean>,
    val startedEpochMs: Long,
) {
    val nights: Int get() = schedule.size
    val lastNightDay: Long get() = startNightDay + nights - 1

    /** True = use it tonight, false = skip it, null = outside the trial. */
    fun assignment(nightDay: Long): Boolean? = schedule.getOrNull((nightDay - startNightDay).toInt().takeIf { it >= 0 } ?: -1)

    fun isOver(todayNightDay: Long): Boolean = todayNightDay > lastNightDay
}

enum class TrialVerdict { TOO_FEW_NIGHTS, HELPS, MAKES_WORSE, NO_CLEAR_DIFFERENCE }

data class TrialResult(
    val nightsWith: Int,
    val nightsWithout: Int,
    val averageWith: Float,
    val averageWithout: Float,
    /** Chance of a difference this big if the remedy did nothing (two-sided permutation test). */
    val pValue: Double,
    val verdict: TrialVerdict,
) {
    val change: Float get() = averageWith - averageWithout
}

object RemedyTrials {
    val LENGTH_CHOICES = listOf(10, 14, 20)
    const val MIN_NIGHTS_EACH = 3
    const val SIGNIFICANCE = 0.05

    /** Equal "with" and "without" nights in a random order. [nights] is rounded up to an even number. */
    fun create(tagId: String, startNightDay: Long, nights: Int, nowMs: Long, random: Random = Random(nowMs)): RemedyTrial {
        val n = (nights + 1) / 2 * 2
        val schedule = (List(n / 2) { true } + List(n / 2) { false }).shuffled(random)
        return RemedyTrial(tagId, startNightDay, schedule, nowMs)
    }

    fun result(trial: RemedyTrial, sessions: List<SessionSummary>, utcOffsetMs: Long, permutations: Int = 5_000, seed: Int = 42): TrialResult {
        // One score per trial night (longest session if a night was split), labelled by the plan.
        val nights = sessions.filter { it.monitoredMs > 0 }
            .groupBy { Trends.nightDay(it.startEpochMs, utcOffsetMs) }
            .mapNotNull { (day, list) -> trial.assignment(day)?.let { it to list.maxBy { s -> s.monitoredMs }.snoreScore.toDouble() } }
        val with = nights.filter { it.first }.map { it.second }
        val without = nights.filter { !it.first }.map { it.second }
        if (with.size < MIN_NIGHTS_EACH || without.size < MIN_NIGHTS_EACH) {
            return TrialResult(with.size, without.size, with.averageOr0(), without.averageOr0(), 1.0, TrialVerdict.TOO_FEW_NIGHTS)
        }
        val observed = with.average() - without.average()
        val all = with + without
        val random = Random(seed)
        var asExtreme = 0
        repeat(permutations) {
            val shuffled = all.shuffled(random)
            val diff = shuffled.take(with.size).average() - shuffled.drop(with.size).average()
            if (abs(diff) >= abs(observed) - 1e-9) asExtreme++
        }
        val p = (asExtreme + 1).toDouble() / (permutations + 1)
        val verdict = when {
            p >= SIGNIFICANCE -> TrialVerdict.NO_CLEAR_DIFFERENCE
            observed < 0 -> TrialVerdict.HELPS
            else -> TrialVerdict.MAKES_WORSE
        }
        return TrialResult(with.size, without.size, with.average().toFloat(), without.average().toFloat(), p, verdict)
    }

    private fun List<Double>.averageOr0() = if (isEmpty()) 0f else average().toFloat()
}
