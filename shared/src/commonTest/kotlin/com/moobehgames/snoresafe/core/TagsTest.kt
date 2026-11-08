package com.moobehgames.snoresafe.core

import com.moobehgames.snoresafe.core.analysis.SnoreBand
import com.moobehgames.snoresafe.core.export.CsvExport
import com.moobehgames.snoresafe.core.session.EndReason
import com.moobehgames.snoresafe.core.session.SessionCodec
import com.moobehgames.snoresafe.core.session.SessionSummary
import com.moobehgames.snoresafe.core.tags.BuiltInTag
import com.moobehgames.snoresafe.core.tags.CustomTag
import com.moobehgames.snoresafe.core.tags.NightLog
import com.moobehgames.snoresafe.core.tags.TagCatalog
import com.moobehgames.snoresafe.core.tags.TagImpacts
import com.moobehgames.snoresafe.core.tags.TagKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TagsTest {
    private fun night(id: String, score: Int) = SessionSummary(
        id = id, startEpochMs = 0, endEpochMs = 3_600_000, monitoredMs = 3_600_000, snoringMs = 0,
        bandMs = mapOf(SnoreBand.QUIET to 3_600_000L), snoreScore = score, peakSnoreDbfs = null, chart = emptyList(),
        interruptions = emptyList(), pauses = emptyList(), otherSoundMs = emptyMap(), endReason = EndReason.USER, algorithmVersion = 1,
    )

    private val tape = BuiltInTag.MOUTH_TAPE.id
    private val wine = BuiltInTag.ALCOHOL.id

    @Test fun impactComparesNightsWithAndWithout() {
        val sessions = listOf(night("a", 10), night("b", 14), night("c", 40), night("d", 36), night("e", 50))
        val logs = mapOf(
            "a" to NightLog("a", setOf(tape)),
            "b" to NightLog("b", setOf(tape)),
            "c" to NightLog("c", setOf(wine)),
            "d" to NightLog("d", setOf(wine)),
            "e" to NightLog("e", setOf(wine)),
        )
        val impacts = TagImpacts.compute(sessions, logs)
        assertEquals(listOf(tape, wine), impacts.map { it.tagId }) // most helpful first
        val t = impacts.first()
        assertEquals(12f, t.averageWith)
        assertEquals(42f, t.averageWithout)
        assertEquals(-30f, t.change)
        assertTrue(impacts.last().change > 0)
    }

    @Test fun needsEnoughNightsOnBothSides() {
        val sessions = listOf(night("a", 10), night("b", 20), night("c", 30))
        val logs = mapOf("a" to NightLog("a", setOf(tape)), "b" to NightLog("b", setOf(tape)), "c" to NightLog("c", setOf(tape)))
        assertTrue(TagImpacts.compute(sessions, logs).isEmpty()) // never a night without it
    }

    @Test fun catalogMixesBuiltInAndCustom() {
        val custom = listOf(CustomTag("c:1", TagKind.REMEDY, "New pillow"))
        val all = TagCatalog.all(custom)
        assertEquals(BuiltInTag.entries.size + 1, all.size)
        assertEquals("New pillow", TagCatalog.find("c:1", custom)?.customName)
        assertEquals(BuiltInTag.CPAP, TagCatalog.builtIn(BuiltInTag.CPAP.id))
        assertFailsWith<IllegalArgumentException> { CustomTag("x", TagKind.FACTOR, "bad id") }
    }

    @Test fun logValidationAndRoundTrip() {
        val log = NightLog("n", setOf(tape), "Felt congested", 82.5f, 4)
        assertEquals(log, SessionCodec.decodeLog(SessionCodec.encodeLog(log)))
        assertFailsWith<IllegalArgumentException> { NightLog("n", restRating = 6) }
        assertTrue(NightLog("n").isEmpty)
    }

    @Test fun csvIncludesUserExtras() {
        val csv = CsvExport.sessions(listOf(night("a", 5)), { "t" }) { CsvExport.Extras("Mouth tape; Alcohol", 4, 80f, "line1\nline2") }
        val row = csv.lines()[1]
        assertTrue(row.endsWith("Mouth tape; Alcohol,4,80.0,line1 line2"), row)
    }
}
