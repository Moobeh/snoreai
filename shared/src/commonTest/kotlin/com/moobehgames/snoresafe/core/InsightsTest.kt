package com.moobehgames.snoresafe.core

import com.moobehgames.snoresafe.core.analysis.NightSound
import com.moobehgames.snoresafe.core.analysis.SnoreBand
import com.moobehgames.snoresafe.core.analysis.SoundEvent
import com.moobehgames.snoresafe.core.audio.AudioClip
import com.moobehgames.snoresafe.core.audio.ClipKind
import com.moobehgames.snoresafe.core.audio.ClipLabel
import com.moobehgames.snoresafe.core.insights.DisturbanceEffect
import com.moobehgames.snoresafe.core.insights.Disturbances
import com.moobehgames.snoresafe.core.insights.HighlightReel
import com.moobehgames.snoresafe.core.insights.RemedyTrials
import com.moobehgames.snoresafe.core.insights.TrialVerdict
import com.moobehgames.snoresafe.core.session.ChartPoint
import com.moobehgames.snoresafe.core.session.EndReason
import com.moobehgames.snoresafe.core.session.SessionSummary
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InsightsTest {
    private val hour = 3_600_000L
    private val day = 24 * hour

    private fun night(dayIndex: Int, score: Int, chart: List<ChartPoint> = emptyList(), events: List<SoundEvent> = emptyList(), other: Map<NightSound, Long> = emptyMap()) =
        SessionSummary(
            id = "n$dayIndex", startEpochMs = dayIndex * day + 22 * hour, endEpochMs = dayIndex * day + 30 * hour,
            monitoredMs = 8 * hour, snoringMs = 0, bandMs = emptyMap(), snoreScore = score, peakSnoreDbfs = null,
            chart = chart, interruptions = emptyList(), pauses = emptyList(), otherSoundMs = other, soundEvents = events,
            endReason = EndReason.USER, algorithmVersion = 1,
        )

    @Test fun trialScheduleIsBalancedAndShuffled() {
        val t = RemedyTrials.create("b:MOUTH_TAPE", 100, 13, 0, Random(1))
        assertEquals(14, t.nights)
        assertEquals(7, t.schedule.count { it })
        assertEquals(t.schedule[0], t.assignment(100))
        assertNull(t.assignment(99))
        assertNull(t.assignment(114))
        assertTrue(t.isOver(114))
    }

    @Test fun clearEffectIsDetected() {
        val t = RemedyTrials.create("b:MOUTH_TAPE", 0, 14, 0, Random(2))
        val r = Random(3)
        val sessions = (0 until 14).map { d -> night(d, if (t.schedule[d]) 10 + r.nextInt(5) else 35 + r.nextInt(5)) }
        val result = RemedyTrials.result(t, sessions, 0)
        assertEquals(TrialVerdict.HELPS, result.verdict)
        assertTrue(result.pValue < 0.01)
        assertTrue(result.change < -15)
    }

    @Test fun noiseIsNotCalledAnEffect() {
        val t = RemedyTrials.create("b:MOUTH_TAPE", 0, 14, 0, Random(4))
        val r = Random(5)
        val sessions = (0 until 14).map { d -> night(d, 20 + r.nextInt(10)) }
        assertEquals(TrialVerdict.NO_CLEAR_DIFFERENCE, RemedyTrials.result(t, sessions, 0).verdict)
        assertEquals(TrialVerdict.TOO_FEW_NIGHTS, RemedyTrials.result(t, sessions.take(3), 0).verdict)
    }

    @Test fun alarmThatStopsSnoringIsFlagged() {
        val snoring = (0 until 60).map { ChartPoint(it, 0.8f, 0.6f, SnoreBand.LOUD) }
        val quiet = (61 until 90).map { ChartPoint(it, 0f, 0f, SnoreBand.QUIET) }
        val start = 22 * hour
        val alarm = SoundEvent(NightSound.CAR_ALARM, "Car alarm", start + 60 * 60_000L, start + 61 * 60_000L, 0.8f)
        val fan = SoundEvent(NightSound.FAN_OR_AC, "Mechanical fan", start, start + 60_000, 0.9f)
        val s = night(0, 30, snoring + quiet, listOf(alarm, fan), mapOf(NightSound.FAN_OR_AC to 6 * hour, NightSound.TRAFFIC to 60_000L))
        val d = Disturbances.analyse(s)
        assertEquals(1, d.size) // the fan is background, not a disturbance
        assertEquals(DisturbanceEffect.SNORING_STOPPED, d.single().effect)
        assertTrue(d.single().quietMinutes >= 20)
        val bg = Disturbances.background(s)
        assertEquals(listOf(NightSound.FAN_OR_AC), bg.map { it.sound }) // traffic under 5% is left out
        assertEquals(75f, bg.single().percentOfNight)
    }

    @Test fun reelPicksLoudestAndNotableWithinThirtySeconds() {
        fun clip(id: String, kind: ClipKind, peak: Float, start: Long, sound: NightSound? = null, label: ClipLabel? = null) =
            AudioClip(id, kind, start, 14_000, "$id.m4a", 1, peak, sound, userLabel = label)
        val clips = (0 until 10).map { clip("s$it", ClipKind.SNORE, it / 10f, it * 1000L) } +
            clip("alarm", ClipKind.SOUND, 0.9f, 500, NightSound.CAR_ALARM) +
            clip("fan", ClipKind.SOUND, 0.9f, 600, NightSound.FAN_OR_AC) +
            clip("wrong", ClipKind.SNORE, 1f, 700, label = ClipLabel.NOT_SNORING)
        val plan = HighlightReel.plan(clips)
        val ids = plan.map { it.clip.id }
        assertTrue("alarm" in ids && "s9" in ids)
        assertTrue("fan" !in ids && "wrong" !in ids && "s0" !in ids)
        assertTrue(plan.sumOf { it.durationMs + HighlightReel.GAP_MS } <= HighlightReel.MAX_TOTAL_MS)
        assertEquals(plan.sortedBy { it.clip.startEpochMs }, plan)
    }
}
