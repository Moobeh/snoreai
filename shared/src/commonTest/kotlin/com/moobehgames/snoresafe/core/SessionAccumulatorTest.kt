package com.moobehgames.snoresafe.core

import com.moobehgames.snoresafe.core.analysis.FrameEvidence
import com.moobehgames.snoresafe.core.analysis.FrameVerdict
import com.moobehgames.snoresafe.core.analysis.NightSound
import com.moobehgames.snoresafe.core.analysis.SnoreBand
import com.moobehgames.snoresafe.core.session.EndReason
import com.moobehgames.snoresafe.core.session.SessionAccumulator
import com.moobehgames.snoresafe.core.session.SessionCodec
import com.moobehgames.snoresafe.core.session.SnoreScore
import com.moobehgames.snoresafe.core.session.TimeSpan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SessionAccumulatorTest {
    private val start = 1_000_000L
    private val hop = 500L

    private val quiet = FrameVerdict(false, 0.1f, SnoreBand.QUIET)
    private val light = FrameVerdict(true, 0.2f, SnoreBand.LIGHT)
    private val epic = FrameVerdict(true, 0.9f, SnoreBand.EPIC)

    /** Feeds [count] contiguous frames starting at [fromMs]; returns the end time. */
    private fun SessionAccumulator.feed(fromMs: Long, count: Int, verdict: FrameVerdict, db: Float = -40f, other: NightSound? = null): Long {
        var t = fromMs
        repeat(count) {
            add(FrameEvidence(t, hop, db, if (verdict.isSnore) 0.9f else 0.1f, other), verdict)
            t += hop
        }
        return t
    }

    @Test fun countsSnoringAndBands() {
        val acc = SessionAccumulator.start("s1", start)
        var t = acc.feed(start, 120, quiet) // 1 min quiet
        t = acc.feed(t, 120, light) // 1 min light
        acc.feed(t, 120, epic, db = -22f) // 1 min epic
        val s = acc.finish(start + 180_000, EndReason.USER)

        assertEquals(180_000L, s.monitoredMs)
        assertEquals(120_000L, s.snoringMs)
        assertEquals(60_000L, s.bandMs[SnoreBand.LIGHT])
        assertEquals(60_000L, s.bandMs[SnoreBand.EPIC])
        assertEquals(-22f, s.peakSnoreDbfs)
        assertEquals(3, s.chart.size)
        assertEquals(SnoreBand.EPIC, s.chart[2].band)
        assertEquals(1f, s.chart[1].snoreFraction)
        // (1*1 + 1*3) weighted minutes / 0.05 h = 80
        assertEquals(80, s.snoreScore)
        assertTrue(s.interruptions.isEmpty())
    }

    @Test fun detectsInterruptionWhenAudioStops() {
        val acc = SessionAccumulator.start("s2", start)
        val t = acc.feed(start, 10, quiet)
        acc.feed(t + 60_000, 10, quiet) // one minute of nothing
        val s = acc.finish(t + 70_000, EndReason.USER)
        assertEquals(listOf(TimeSpan(t, t + 60_000)), s.interruptions)
        assertEquals(10_000L, s.monitoredMs)
    }

    @Test fun pauseIsNotAnInterruption() {
        val acc = SessionAccumulator.start("s3", start)
        val t = acc.feed(start, 10, quiet)
        acc.pause(t)
        acc.feed(t, 10, epic) // ignored while paused
        acc.resume(t + 120_000)
        acc.feed(t + 120_000, 10, quiet)
        val s = acc.finish(t + 130_000, EndReason.USER)
        assertTrue(s.interruptions.isEmpty())
        assertEquals(listOf(TimeSpan(t, t + 120_000)), s.pauses)
        assertEquals(0L, s.snoringMs)
    }

    @Test fun otherSoundsAreTracked() {
        val acc = SessionAccumulator.start("s4", start)
        acc.feed(start, 4, quiet, other = NightSound.COUGH)
        assertEquals(2_000L, acc.finish(start + 2_000, EndReason.USER).otherSoundMs[NightSound.COUGH])
    }

    @Test fun checkpointRoundTripResumesExactly() {
        val acc = SessionAccumulator.start("s5", start)
        val t = acc.feed(start, 50, light)
        val restored = SessionAccumulator(SessionCodec.decodeCheckpoint(SessionCodec.encodeCheckpoint(acc.state)))
        assertEquals(acc.state, restored.state)
        val summary = restored.finish(t, EndReason.RECOVERED)
        assertEquals(EndReason.RECOVERED, summary.endReason)
        assertEquals(summary, SessionCodec.decodeSummary(SessionCodec.encodeSummary(summary)))
    }

    @Test fun scoreIsZeroForTinySessions() {
        assertEquals(0, SnoreScore.compute(mapOf(SnoreBand.EPIC to 30_000L), 30_000L))
    }

    @Test fun scoreForFullHourOfEpicIs180() {
        assertEquals(180, SnoreScore.compute(mapOf(SnoreBand.EPIC to 3_600_000L), 3_600_000L))
    }
}
