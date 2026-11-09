package com.moobehgames.snoresafe.core.insights

import com.moobehgames.snoresafe.core.audio.AudioClip
import com.moobehgames.snoresafe.core.audio.ClipKind

/** One piece of the morning highlight reel: a slice of a recording. */
data class ReelPart(val clip: AudioClip, val fromMs: Long, val durationMs: Long)

/** Picks the night's loudest snores and most notable sounds for a short, shareable montage. */
object HighlightReel {
    const val MAX_TOTAL_MS = 30_000L
    const val PART_MS = 6_000L
    const val GAP_MS = 400L
    private const val SKIP_PRE_ROLL_MS = 2_000L
    private const val MAX_SOUNDS = 2

    fun plan(clips: List<AudioClip>): List<ReelPart> {
        val sounds = clips.filter { it.kind == ClipKind.SOUND && it.sound?.notable == true }.sortedByDescending { it.peak }.take(MAX_SOUNDS)
        val snores = clips.filter { it.kind == ClipKind.SNORE && it.userLabel != com.moobehgames.snoresafe.core.audio.ClipLabel.NOT_SNORING }
            .sortedByDescending { it.peak }
        val picked = mutableListOf<ReelPart>()
        var total = 0L
        for (clip in sounds + snores) {
            val from = if (clip.durationMs > SKIP_PRE_ROLL_MS + 1_000) SKIP_PRE_ROLL_MS else 0L
            val length = minOf(PART_MS, clip.durationMs - from)
            if (length < 1_000) continue
            if (total + length + GAP_MS > MAX_TOTAL_MS) break
            picked += ReelPart(clip, from, length)
            total += length + GAP_MS
        }
        // Play in the order they happened, like a story of the night.
        return picked.sortedBy { it.clip.startEpochMs }
    }
}
