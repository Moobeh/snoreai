package com.moobehgames.snoresafe.core.analysis

import kotlinx.serialization.Serializable
import kotlin.math.max

/** One occurrence of a non-snore sound: "car alarm, 02:14–02:15". */
@Serializable
data class SoundEvent(
    val sound: NightSound,
    /** The classifier's specific label, e.g. "Car alarm". */
    val label: String,
    val startEpochMs: Long,
    val endEpochMs: Long,
    val peakConfidence: Float,
) {
    val durationMs: Long get() = endEpochMs - startEpochMs
}

/**
 * Groups per-frame detections into events. Frames of the same sound separated by less than
 * [mergeGapMs] belong to one event. Pure and serialisable (via [open]) so it survives checkpoints.
 */
object SoundEventTracker {
    const val DEFAULT_MERGE_GAP_MS = 3_000L

    data class Step(val open: SoundEvent?, val closed: SoundEvent?)

    fun onFrame(
        open: SoundEvent?,
        frame: FrameEvidence,
        isSnore: Boolean,
        mergeGapMs: Long = DEFAULT_MERGE_GAP_MS,
    ): Step {
        val sound = if (isSnore) null else frame.otherSound
        val frameEnd = frame.timestampMs + frame.durationMs

        if (sound == null) {
            // Nothing heard: close the open event once the gap is long enough.
            return if (open != null && frame.timestampMs - open.endEpochMs >= mergeGapMs) Step(null, open) else Step(open, null)
        }
        if (open != null && open.sound == sound && frame.timestampMs - open.endEpochMs < mergeGapMs) {
            val louder = frame.otherConfidence > open.peakConfidence
            return Step(
                open.copy(
                    endEpochMs = max(open.endEpochMs, frameEnd),
                    peakConfidence = max(open.peakConfidence, frame.otherConfidence),
                    label = if (louder) frame.otherLabel ?: open.label else open.label,
                ),
                null,
            )
        }
        val started = SoundEvent(sound, frame.otherLabel ?: sound.name, frame.timestampMs, frameEnd, frame.otherConfidence)
        return Step(started, open)
    }
}
