package com.moobehgames.snoresafe.core.insights

import com.moobehgames.snoresafe.core.analysis.NightSound
import com.moobehgames.snoresafe.core.analysis.SnoreBand
import com.moobehgames.snoresafe.core.analysis.SoundEvent
import com.moobehgames.snoresafe.core.session.SessionSummary

/** What a sound seemed to do to the night. Careful wording: these are patterns, not proof. */
enum class DisturbanceEffect {
    /** Snoring before, then a long quiet stretch: often a sign of waking or lighter sleep. */
    SNORING_STOPPED,

    /** Snoring got noticeably louder afterwards. */
    SNORING_LOUDER,

    /** No clear change in snoring. */
    NO_CLEAR_CHANGE,
}

data class Disturbance(val event: SoundEvent, val effect: DisturbanceEffect, val quietMinutes: Int)

data class BackgroundNoise(val sound: NightSound, val ms: Long, val percentOfNight: Float)

/**
 * Links sounds heard in the night to what happened to snoring around them, and summarises
 * steady background noise in the bedroom.
 */
object Disturbances {
    /** Steady sounds that form the bedroom's background rather than one-off disturbances. */
    val BACKGROUND = setOf(NightSound.FAN_OR_AC, NightSound.APPLIANCE, NightSound.TRAFFIC, NightSound.RAIN, NightSound.WIND, NightSound.MEDIA, NightSound.INSECTS)

    /** Sounds that are part of the sleeper themselves, not the room. */
    private val SELF = setOf(NightSound.BREATHING, NightSound.BODY, NightSound.COUGH, NightSound.SPEECH)

    private const val WINDOW_MIN = 20
    private const val SNORING_BEFORE = 0.3f
    private const val STOPPED_MIN = 10

    fun analyse(s: SessionSummary, minConfidence: Float = 0.45f): List<Disturbance> {
        val byMinute = s.chart.associateBy { it.minuteIndex }
        fun fraction(m: Int) = byMinute[m]?.snoreFraction ?: 0f
        fun weight(m: Int) = byMinute[m]?.let { if (it.band == SnoreBand.QUIET) 0f else it.band.weight * it.snoreFraction } ?: 0f

        return s.soundEvents
            .filter { it.sound !in BACKGROUND && it.sound !in SELF && it.peakConfidence >= minConfidence }
            .map { e ->
                val m = ((e.startEpochMs - s.startEpochMs) / 60_000L).toInt()
                val before = (m - WINDOW_MIN until m).filter { it >= 0 }
                val after = (m + 1..m + WINDOW_MIN)
                val snoringBefore = before.isNotEmpty() && before.map(::fraction).average() >= SNORING_BEFORE
                val quietRun = after.takeWhile { fraction(it) < 0.1f }.count()
                val louder = before.isNotEmpty() && after.map(::weight).average() > before.map(::weight).average() * 1.5 + 0.2
                when {
                    snoringBefore && quietRun >= STOPPED_MIN -> Disturbance(e, DisturbanceEffect.SNORING_STOPPED, quietRun)
                    louder -> Disturbance(e, DisturbanceEffect.SNORING_LOUDER, 0)
                    else -> Disturbance(e, DisturbanceEffect.NO_CLEAR_CHANGE, 0)
                }
            }
            .sortedBy { it.event.startEpochMs }
    }

    fun background(s: SessionSummary, minPercent: Float = 5f): List<BackgroundNoise> {
        if (s.monitoredMs <= 0) return emptyList()
        return s.otherSoundMs.filterKeys { it in BACKGROUND }
            .map { (sound, ms) -> BackgroundNoise(sound, ms, ms * 100f / s.monitoredMs) }
            .filter { it.percentOfNight >= minPercent }
            .sortedByDescending { it.ms }
    }
}
