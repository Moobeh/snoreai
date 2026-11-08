package com.moobehgames.snoresafe.core

import com.moobehgames.snoresafe.core.analysis.NightSound
import com.moobehgames.snoresafe.core.analysis.SnoreBand
import com.moobehgames.snoresafe.core.analysis.SoundEvent
import com.moobehgames.snoresafe.core.export.CsvExport
import com.moobehgames.snoresafe.core.session.EndReason
import com.moobehgames.snoresafe.core.session.SessionCodec
import com.moobehgames.snoresafe.core.session.SessionSummary
import com.moobehgames.snoresafe.core.settings.AppSettings
import com.moobehgames.snoresafe.core.support.SupportMoment
import com.moobehgames.snoresafe.core.support.SupportPolicy
import com.moobehgames.snoresafe.core.support.SupportState
import com.moobehgames.snoresafe.core.trends.TrendPeriod
import com.moobehgames.snoresafe.core.trends.Trends
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrendsSupportCsvTest {
    private val hour = 3_600_000L
    private val day = 24 * hour

    private fun night(dayIndex: Int, score: Int, id: String = "n$dayIndex", hours: Long = 7) = SessionSummary(
        id = id, startEpochMs = dayIndex * day + 22 * hour, endEpochMs = dayIndex * day + (22 + hours) * hour,
        monitoredMs = hours * hour, snoringMs = hours * hour / 4, bandMs = mapOf(SnoreBand.LOUD to hours * hour / 4),
        snoreScore = score, peakSnoreDbfs = -30f, chart = emptyList(), interruptions = emptyList(), pauses = emptyList(),
        otherSoundMs = emptyMap(), endReason = EndReason.USER, algorithmVersion = 1,
    )

    @Test fun earlyMorningSessionBelongsToPreviousNight() {
        assertEquals(Trends.nightDay(10 * day + 22 * hour, 0), Trends.nightDay(11 * day + 1 * hour, 0))
        assertEquals(10L, Trends.nightDay(11 * day + 1 * hour, 0))
    }

    @Test fun weekComparison() {
        val sessions = (0 until 14).map { night(it, if (it < 7) 40 else 20) }
        val c = Trends.compare(sessions, TrendPeriod.WEEK, todayNightDay = 13, utcOffsetMs = 0)
        assertEquals(7, c.current?.nights)
        assertEquals(20f, c.current?.averageScore)
        assertEquals(-20f, c.scoreChange)
        assertEquals(25f, c.current!!.averageSnoringPercent, 0.01f)
    }

    @Test fun nightPointsCombineSplitSessions() {
        val a = night(3, 10, "a", hours = 3)
        val b = night(3, 40, "b", hours = 1).copy(startEpochMs = 4 * day + 2 * hour)
        val points = Trends.nightPoints(listOf(a, b), 0)
        assertEquals(1, points.size)
        assertEquals(18, points.single().score) // (10*3 + 40*1) / 4 = 17.5
    }

    @Test fun supportCardRules() {
        val fresh = SupportState()
        assertTrue(SupportPolicy.shouldShowCard(fresh, SupportMoment.SEVENTH_NIGHT, 0, onboardingDone = true, recording = false))
        assertFalse(SupportPolicy.shouldShowCard(fresh, null, 0, true, false))
        assertFalse(SupportPolicy.shouldShowCard(fresh, SupportMoment.EXPORT_DONE, 0, onboardingDone = false, recording = false))
        assertFalse(SupportPolicy.shouldShowCard(fresh, SupportMoment.EXPORT_DONE, 0, true, recording = true))
        val shown = SupportPolicy.shown(fresh, 1_000)
        assertFalse(SupportPolicy.shouldShowCard(shown, SupportMoment.EXPORT_DONE, 1_000 + day, true, false))
        assertTrue(SupportPolicy.shouldShowCard(shown, SupportMoment.EXPORT_DONE, 1_000 + SupportPolicy.MIN_INTERVAL_MS, true, false))
        val done = SupportPolicy.supported(shown)
        assertFalse(SupportPolicy.shouldShowCard(done, SupportMoment.EXPORT_DONE, Long.MAX_VALUE, true, false))
        assertFalse(SupportPolicy.showSettingsEntry(done))
    }

    @Test fun supportMoments() {
        val history = (0 until 6).map { night(it, 30) }
        assertEquals(SupportMoment.SEVENTH_NIGHT, SupportPolicy.momentAfterNight(night(6, 50), history))
        val longer = (0 until 8).map { night(it, 30) }
        assertEquals(SupportMoment.NEW_LOWEST_SCORE, SupportPolicy.momentAfterNight(night(9, 10), longer))
        assertNull(SupportPolicy.momentAfterNight(night(9, 35), longer))
    }

    @Test fun csvEscapesAndListsNotableSounds() {
        val n = night(0, 12).copy(
            soundEvents = listOf(SoundEvent(NightSound.CAR_ALARM, "Car alarm", 0, 1000, 0.8f), SoundEvent(NightSound.CAR_ALARM, "Car alarm", 5000, 6000, 0.7f)),
            otherSoundMs = mapOf(NightSound.CAR_ALARM to 120_000L),
        )
        val csv = CsvExport.sessions(listOf(n), { "t$it" })
        val row = csv.lines()[1]
        assertTrue(row.contains("car alarm x2"), row)
        assertEquals("\"a,b\"", CsvExport.escape("a,b"))
        assertEquals("'=SUM(A1)", CsvExport.escape("=SUM(A1)"))
        assertEquals("-3.5", CsvExport.escape("-3.5"))
    }

    @Test fun settingsSurviveRoundTripAndBadFilesFallBack() {
        val s = AppSettings(startDelayMinutes = 10, saveOtherSoundClips = false)
        assertEquals(s, SessionCodec.decodeSettings(SessionCodec.encodeSettings(s)))
        assertEquals(AppSettings(), SessionCodec.decodeSettings("{not json"))
    }

    @Test fun lightFilterDropsLightSnoring() {
        val d = com.moobehgames.snoresafe.core.analysis.SnoreDetector(
            com.moobehgames.snoresafe.core.analysis.DetectionSettings(countLightSnoring = false),
        )
        val light = d.assess(com.moobehgames.snoresafe.core.analysis.FrameEvidence(0, 500, -50f, 0.9f))
        val loud = d.assess(com.moobehgames.snoresafe.core.analysis.FrameEvidence(0, 500, -35f, 0.9f))
        assertFalse(light.isSnore)
        assertTrue(loud.isSnore)
    }
}
