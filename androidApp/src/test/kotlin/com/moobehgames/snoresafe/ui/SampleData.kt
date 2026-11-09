package com.moobehgames.snoresafe.ui

import com.moobehgames.snoresafe.core.analysis.NightSound
import com.moobehgames.snoresafe.core.analysis.SnoreBand
import com.moobehgames.snoresafe.core.analysis.SoundEvent
import com.moobehgames.snoresafe.core.audio.AudioClip
import com.moobehgames.snoresafe.core.audio.ClipKind
import com.moobehgames.snoresafe.core.audio.SessionAudio
import com.moobehgames.snoresafe.core.session.ChartPoint
import com.moobehgames.snoresafe.core.session.EndReason
import com.moobehgames.snoresafe.core.session.SessionSummary
import java.util.Calendar
import kotlin.math.sin
import kotlin.random.Random

/** Realistic-looking nights for screenshots and render tests. */
object SampleData {
    private const val MIN = 60_000L
    private const val HOUR = 60 * MIN

    /** 23:10 on the night [daysAgo] days before today. */
    private fun bedtime(daysAgo: Int): Long = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, -daysAgo - 1)
        set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 10); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun night(daysAgo: Int = 0, intensity: Float = 1f, seed: Int = 1): SessionSummary {
        val r = Random(seed)
        val start = bedtime(daysAgo)
        val minutes = 440 + r.nextInt(40)
        val chart = (0 until minutes).mapNotNull { m ->
            val wave = (sin(m / 38.0) + 1) / 2 * intensity
            val fraction = (wave * r.nextFloat() * 1.3f).toFloat().coerceIn(0f, 1f)
            if (fraction < 0.15f) return@mapNotNull null
            val band = when {
                fraction > 0.75f -> SnoreBand.EPIC
                fraction > 0.45f -> SnoreBand.LOUD
                else -> SnoreBand.LIGHT
            }
            ChartPoint(m, fraction, fraction, band)
        }
        val bandMs = chart.groupBy { it.band }.mapValues { (_, v) -> (v.sumOf { it.snoreFraction.toDouble() } * MIN).toLong() }
        val snoring = bandMs.values.sum()
        val monitored = minutes * MIN
        val score = (((bandMs[SnoreBand.LIGHT] ?: 0) + 2 * (bandMs[SnoreBand.LOUD] ?: 0) + 3 * (bandMs[SnoreBand.EPIC] ?: 0)) / MIN.toDouble() / (monitored / HOUR.toDouble())).toInt()
        val events = listOf(
            SoundEvent(NightSound.CAR_ALARM, "Car alarm", start + 132 * MIN, start + 133 * MIN, 0.81f),
            SoundEvent(NightSound.BABY, "Baby cry, infant cry", start + 251 * MIN, start + 253 * MIN, 0.74f),
            SoundEvent(NightSound.SPEECH, "Speech", start + 61 * MIN, start + 61 * MIN + 20_000, 0.62f),
            SoundEvent(NightSound.SPEECH, "Speech", start + 300 * MIN, start + 300 * MIN + 12_000, 0.55f),
            SoundEvent(NightSound.COUGH, "Cough", start + 190 * MIN, start + 190 * MIN + 4_000, 0.66f),
        )
        return SessionSummary(
            id = "night-$daysAgo", startEpochMs = start, endEpochMs = start + monitored, monitoredMs = monitored,
            snoringMs = snoring, bandMs = bandMs, snoreScore = score, peakSnoreDbfs = -24f, chart = chart,
            interruptions = emptyList(), pauses = emptyList(),
            otherSoundMs = mapOf(NightSound.SPEECH to 32_000L, NightSound.COUGH to 4_000L, NightSound.FAN_OR_AC to 95 * MIN, NightSound.CAR_ALARM to MIN, NightSound.BABY to 2 * MIN),
            soundEvents = events, endReason = EndReason.USER, algorithmVersion = 1,
        )
    }

    fun history(): List<SessionSummary> =
        (0 until 26).filter { it % 7 != 3 }.map { d -> night(d, intensity = 0.35f + (d % 9) / 10f + if (d > 14) 0.3f else 0f, seed = d + 2) }

    /** Mouth tape on quieter nights, alcohol on louder ones, so "What helps" has something to show. */
    fun logs(history: List<SessionSummary>): Map<String, com.moobehgames.snoresafe.core.tags.NightLog> = history.associate { s ->
        val tags = buildSet {
            if (s.snoreScore < 25) add(com.moobehgames.snoresafe.core.tags.BuiltInTag.MOUTH_TAPE.id)
            if (s.snoreScore > 35) add(com.moobehgames.snoresafe.core.tags.BuiltInTag.ALCOHOL.id)
            if (s.id.hashCode() % 3 == 0) add(com.moobehgames.snoresafe.core.tags.BuiltInTag.SIDE_SLEEPING.id)
        }
        s.id to com.moobehgames.snoresafe.core.tags.NightLog(s.id, tags, restRating = if (s.snoreScore < 25) 4 else 2)
    }

    fun audio(s: SessionSummary): SessionAudio {
        fun clip(id: String, kind: ClipKind, minute: Int, peak: Float, sound: NightSound? = null, pinned: Boolean = false) =
            AudioClip(id, kind, s.startEpochMs + minute * MIN, 14_000, "$id.m4a", 60_000, peak, sound, sound?.name, pinned, features = listOf(0.1f), userLabel = if (id == "s1") com.moobehgames.snoresafe.core.audio.ClipLabel.SNORING else null)
        return SessionAudio(
            s.id,
            listOf(
                clip("s1", ClipKind.SNORE, 47, 0.86f, pinned = true),
                clip("s2", ClipKind.SNORE, 112, 0.58f),
                clip("s3", ClipKind.SNORE, 205, 0.31f),
                clip("a1", ClipKind.SOUND, 132, 0.81f, NightSound.CAR_ALARM),
                clip("b1", ClipKind.SOUND, 251, 0.74f, NightSound.BABY),
                clip("t1", ClipKind.SOUND, 61, 0.62f, NightSound.SPEECH),
            ),
        )
    }
}
