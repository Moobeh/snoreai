package com.moobehgames.snoresafe.core

import com.moobehgames.snoresafe.core.analysis.FrameEvidence
import com.moobehgames.snoresafe.core.analysis.FrameVerdict
import com.moobehgames.snoresafe.core.analysis.NightSound
import com.moobehgames.snoresafe.core.analysis.SnoreBand
import com.moobehgames.snoresafe.core.analysis.SoundTaxonomy
import com.moobehgames.snoresafe.core.session.EndReason
import com.moobehgames.snoresafe.core.session.SessionAccumulator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SoundEventsTest {
    private val quiet = FrameVerdict(false, 0.1f, SnoreBand.QUIET)
    private val snore = FrameVerdict(true, 0.5f, SnoreBand.LOUD)

    private fun SessionAccumulator.frame(t: Long, sound: NightSound?, label: String? = null, conf: Float = 0.6f, verdict: FrameVerdict = quiet) =
        add(FrameEvidence(t, 500, -40f, 0.1f, sound, label, if (sound == null) 0f else conf), verdict)

    @Test fun consecutiveFramesFormOneEvent() {
        val acc = SessionAccumulator.start("s", 0)
        for (i in 0 until 10) acc.frame(i * 500L, NightSound.CAR_ALARM, "Car alarm")
        val s = acc.finish(10_000, EndReason.USER)
        assertEquals(1, s.soundEvents.size)
        val e = s.soundEvents.single()
        assertEquals(NightSound.CAR_ALARM, e.sound)
        assertEquals("Car alarm", e.label)
        assertEquals(0L, e.startEpochMs)
        assertEquals(5_000L, e.endEpochMs)
    }

    @Test fun shortGapMergesLongGapSplits() {
        val acc = SessionAccumulator.start("s", 0)
        acc.frame(0, NightSound.BABY)
        acc.frame(500, null)
        acc.frame(1_000, NightSound.BABY) // 500 ms gap → same event
        for (i in 0 until 10) acc.frame(1_500 + i * 500L, null) // 5 s of quiet → closes
        acc.frame(7_000, NightSound.BABY)
        val s = acc.finish(8_000, EndReason.USER)
        assertEquals(2, s.soundEvents.count { it.sound == NightSound.BABY })
    }

    @Test fun differentSoundStartsNewEvent() {
        val acc = SessionAccumulator.start("s", 0)
        acc.frame(0, NightSound.PET)
        acc.frame(500, NightSound.DOORBELL)
        val s = acc.finish(1_000, EndReason.USER)
        assertEquals(listOf(NightSound.PET, NightSound.DOORBELL), s.soundEvents.map { it.sound })
    }

    @Test fun snoringFramesDoNotCountAsOtherSounds() {
        val acc = SessionAccumulator.start("s", 0)
        acc.frame(0, NightSound.BREATHING, verdict = snore)
        assertTrue(acc.finish(500, EndReason.USER).soundEvents.isEmpty())
    }

    @Test fun eventSurvivesCheckpointWhileOpen() {
        val acc = SessionAccumulator.start("s", 0)
        acc.frame(0, NightSound.SMOKE_ALARM, "Smoke detector, smoke alarm", conf = 0.9f)
        val restored = SessionAccumulator(
            com.moobehgames.snoresafe.core.session.SessionCodec.decodeCheckpoint(
                com.moobehgames.snoresafe.core.session.SessionCodec.encodeCheckpoint(acc.state),
            ),
        )
        assertEquals(0.9f, restored.finish(1_000, EndReason.RECOVERED).soundEvents.single().peakConfidence)
    }

    @Test fun notableSoundBeatsCommonSoundAtLowerScore() {
        val labels = listOf("Snoring", "Speech", "Car alarm")
        val t = SoundTaxonomy(labels)
        val scores = floatArrayOf(0f, 0.40f, 0.33f) // speech clears 0.35 by 0.05, car alarm clears 0.25 by 0.08
        assertEquals(NightSound.CAR_ALARM, t.evidence(scores).otherSound)
        assertEquals("Car alarm", t.evidence(scores).otherLabel)
    }
}
