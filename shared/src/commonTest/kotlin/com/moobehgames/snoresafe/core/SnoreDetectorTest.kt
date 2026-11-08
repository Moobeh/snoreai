package com.moobehgames.snoresafe.core

import com.moobehgames.snoresafe.core.analysis.DetectionSettings
import com.moobehgames.snoresafe.core.analysis.FrameEvidence
import com.moobehgames.snoresafe.core.analysis.Loudness
import com.moobehgames.snoresafe.core.analysis.SnoreBand
import com.moobehgames.snoresafe.core.analysis.SnoreDetector
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SnoreDetectorTest {
    private val detector = SnoreDetector(DetectionSettings())

    private fun frame(db: Float, p: Float) = FrameEvidence(0L, 500L, db, p)

    @Test fun confidentLoudSnoreIsEpic() {
        val v = detector.assess(frame(db = -20f, p = 0.9f))
        assertTrue(v.isSnore)
        assertEquals(SnoreBand.EPIC, v.band)
    }

    @Test fun lowProbabilityIsNotSnore() {
        val v = detector.assess(frame(db = -20f, p = 0.3f))
        assertFalse(v.isSnore)
        assertEquals(SnoreBand.QUIET, v.band)
    }

    @Test fun tooQuietIsNotSnoreEvenIfConfident() {
        assertFalse(detector.assess(frame(db = -70f, p = 0.99f)).isSnore)
    }

    @Test fun bandsFollowIntensity() {
        // floor -60, ceiling -20: -50 → 0.25 light, -40 → 0.5 loud, -28 → 0.8 epic
        assertEquals(SnoreBand.LIGHT, detector.assess(frame(-50f, 0.9f)).band)
        assertEquals(SnoreBand.LOUD, detector.assess(frame(-40f, 0.9f)).band)
        assertEquals(SnoreBand.EPIC, detector.assess(frame(-28f, 0.9f)).band)
    }

    @Test fun sensitivityMovesThreshold() {
        val strict = SnoreDetector(DetectionSettings(sensitivity = 0f))
        val loose = SnoreDetector(DetectionSettings(sensitivity = 1f))
        assertFalse(strict.assess(frame(-30f, 0.5f)).isSnore)
        assertTrue(loose.assess(frame(-30f, 0.5f)).isSnore)
    }

    @Test fun volumeOffsetShiftsIntensity() {
        val boosted = SnoreDetector(DetectionSettings(volumeOffsetDb = 10f))
        assertTrue(boosted.intensityOf(-40f) > detector.intensityOf(-40f))
    }

    @Test fun loudnessOfFullScaleSineIsAboutMinus3Db() {
        val n = 1600
        val samples = FloatArray(n) { kotlin.math.sin(2 * kotlin.math.PI * 440 * it / 16000).toFloat() }
        val db = Loudness.dbfs(Loudness.rms(samples))
        assertTrue(db in -3.2f..-2.8f, "was $db")
        assertEquals(Loudness.SILENCE_DBFS, Loudness.dbfs(Loudness.rms(FloatArray(10))))
    }
}
