package com.moobehgames.snoresafe.core

import com.moobehgames.snoresafe.core.analysis.NightSound
import com.moobehgames.snoresafe.core.audio.AudioClip
import com.moobehgames.snoresafe.core.audio.ClipKind
import com.moobehgames.snoresafe.core.audio.ClipSelection
import com.moobehgames.snoresafe.core.audio.SessionAudio
import com.moobehgames.snoresafe.core.audio.StorageCleanup
import com.moobehgames.snoresafe.core.settings.RecordingMode
import com.moobehgames.snoresafe.core.settings.StorageSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StorageAndClipsTest {
    private val mb = 1024L * 1024

    private fun clip(id: String, kind: ClipKind = ClipKind.SNORE, peak: Float = 0.5f, size: Long = mb, start: Long = 0, sound: NightSound? = null, pinned: Boolean = false) =
        AudioClip(id, kind, start, 10_000, "$id.m4a", size, peak, sound, pinned = pinned)

    @Test fun snoreClipsModeKeepsLoudestAndBestPerSound() {
        val snores = (0 until 30).map { clip("s$it", peak = it / 30f) }
        val babies = (0 until 5).map { clip("b$it", ClipKind.SOUND, peak = it / 5f, sound = NightSound.BABY) }
        val del = ClipSelection.toDelete(snores + babies + clip("p", pinned = true, peak = 0f), RecordingMode.SNORE_CLIPS)
        assertEquals(10 + 2, del.size)
        assertTrue("s29" !in del && "s0" in del && "b4" !in del && "b0" in del && "p" !in del)
        assertTrue(ClipSelection.toDelete(snores, RecordingMode.ALL_SNORING).isEmpty())
    }

    @Test fun recentNightsKeepEverythingOlderNightsAreTrimmed() {
        val settings = StorageSettings(limitMb = 10_000, keepAllAudioNights = 1, clipsPerOlderNight = 1)
        val recent = SessionAudio("n1", listOf(clip("a"), clip("b")))
        val older = SessionAudio("n2", listOf(clip("c", peak = 0.9f), clip("d", peak = 0.1f), clip("seg", ClipKind.SEGMENT), clip("pin", pinned = true)))
        val protected = SessionAudio("n3", listOf(clip("e"), clip("f")), protected = true)
        val del = StorageCleanup.clipsToDelete(listOf(recent, older, protected), settings)
        assertNull(del["n1"])
        assertEquals(setOf("d", "seg"), del["n2"])
        assertNull(del["n3"])
    }

    @Test fun overLimitDeletesOldestFirstButNeverPinnedOrProtected() {
        val settings = StorageSettings(limitMb = 3, keepAllAudioNights = 10, clipsPerOlderNight = 10)
        val newest = SessionAudio("new", listOf(clip("n1"), clip("n2")))
        val oldest = SessionAudio("old", listOf(clip("o1"), clip("o2", pinned = true)))
        val del = StorageCleanup.clipsToDelete(listOf(newest, oldest), settings)
        assertEquals(setOf("o1"), del["old"])
        assertNull(del["new"])
    }

    @Test fun timelineTapFindsCoveringOrNearestClip() {
        val audio = SessionAudio(
            "n",
            listOf(
                clip("seg", ClipKind.SEGMENT, start = 0).copy(durationMs = 600_000),
                clip("snore", start = 100_000),
            ),
        )
        assertEquals("snore", audio.clipAt(105_000)?.id)
        assertEquals("seg", audio.clipAt(300_000)?.id)
        assertEquals("snore", SessionAudio("n", listOf(clip("snore", start = 100_000))).clipAt(130_000)?.id)
        assertNull(SessionAudio("n", listOf(clip("snore", start = 100_000))).clipAt(900_000))
    }
}
