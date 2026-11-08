package com.moobehgames.snoresafe.core.audio

import com.moobehgames.snoresafe.core.analysis.NightSound
import com.moobehgames.snoresafe.core.settings.RecordingMode
import kotlinx.serialization.Serializable

@Serializable
enum class ClipKind {
    /** A snoring episode. */
    SNORE,

    /** A non-snore sound: alarm, baby, talking… */
    SOUND,

    /** A slice of a full-night recording. */
    SEGMENT,
}

@Serializable
data class AudioClip(
    val id: String,
    val kind: ClipKind,
    val startEpochMs: Long,
    val durationMs: Long,
    val fileName: String,
    val sizeBytes: Long,
    /** Snore intensity (0..1) for SNORE clips, classifier confidence for SOUND clips. */
    val peak: Float,
    val sound: NightSound? = null,
    val label: String? = null,
    val pinned: Boolean = false,
    /** Mean classifier scores over the clip, kept for on-device personalisation. */
    val features: List<Float>? = null,
) {
    val endEpochMs: Long get() = startEpochMs + durationMs

    fun covers(epochMs: Long): Boolean = epochMs in startEpochMs until endEpochMs
}

/** All audio for one night. */
@Serializable
data class SessionAudio(
    val sessionId: String,
    val clips: List<AudioClip> = emptyList(),
    /** Protected nights are never cleaned up automatically. */
    val protected: Boolean = false,
) {
    val sizeBytes: Long get() = clips.sumOf { it.sizeBytes }

    /** Clip to play for a moment on the timeline: an event clip covering it, else a full-night segment, else the nearest clip. */
    fun clipAt(epochMs: Long, toleranceMs: Long = 60_000L): AudioClip? {
        val events = clips.filter { it.kind != ClipKind.SEGMENT }
        events.firstOrNull { it.covers(epochMs) }?.let { return it }
        clips.firstOrNull { it.kind == ClipKind.SEGMENT && it.covers(epochMs) }?.let { return it }
        return events.minByOrNull { distance(it, epochMs) }?.takeIf { distance(it, epochMs) <= toleranceMs }
    }

    private fun distance(c: AudioClip, t: Long): Long = when {
        t < c.startEpochMs -> c.startEpochMs - t
        t >= c.endEpochMs -> t - c.endEpochMs
        else -> 0L
    }
}

object ClipRanking {
    /** Higher = more worth keeping. Pinned first, then notable sounds, then loud snores. */
    fun priority(c: AudioClip): Float = when {
        c.pinned -> 10f
        c.kind == ClipKind.SOUND && c.sound?.notable == true -> 5f + c.peak
        c.kind == ClipKind.SNORE -> 2f + c.peak
        c.kind == ClipKind.SOUND -> 1f + c.peak
        else -> 0f
    }
}

/** Which clips a night keeps when it ends, for each recording mode. */
object ClipSelection {
    const val SNORE_CLIPS_KEEP_SNORES = 20
    const val SNORE_CLIPS_KEEP_PER_SOUND = 3

    fun toDelete(clips: List<AudioClip>, mode: RecordingMode): Set<String> {
        if (mode != RecordingMode.SNORE_CLIPS) return emptySet()
        val keep = mutableSetOf<String>()
        clips.filter { it.pinned }.forEach { keep += it.id }
        clips.filter { it.kind == ClipKind.SNORE }.sortedByDescending { it.peak }.take(SNORE_CLIPS_KEEP_SNORES).forEach { keep += it.id }
        clips.filter { it.kind == ClipKind.SOUND }.groupBy { it.sound }.values.forEach { group ->
            group.sortedByDescending { it.peak }.take(SNORE_CLIPS_KEEP_PER_SOUND).forEach { keep += it.id }
        }
        return clips.map { it.id }.filterNot { it in keep }.toSet()
    }
}
