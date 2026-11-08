package com.moobehgames.snoresafe.core.session

import com.moobehgames.snoresafe.core.analysis.FrameEvidence
import com.moobehgames.snoresafe.core.analysis.FrameVerdict
import com.moobehgames.snoresafe.core.analysis.NightSound
import com.moobehgames.snoresafe.core.analysis.SnoreBand
import kotlinx.serialization.Serializable
import kotlin.math.max

@Serializable
data class MinuteBucket(
    val frameMs: Long = 0L,
    val snoreMs: Long = 0L,
    val peakIntensity: Float = 0f,
    val maxBand: SnoreBand = SnoreBand.QUIET,
)

/** Everything needed to rebuild a session after the app is killed. Saved as a checkpoint. */
@Serializable
data class AccumulatorState(
    val sessionId: String,
    val startEpochMs: Long,
    val lastFrameEndMs: Long,
    val monitoredMs: Long = 0L,
    val snoringMs: Long = 0L,
    val bandMs: Map<SnoreBand, Long> = emptyMap(),
    val peakSnoreDbfs: Float? = null,
    val minutes: Map<Int, MinuteBucket> = emptyMap(),
    val interruptions: List<TimeSpan> = emptyList(),
    val pauses: List<TimeSpan> = emptyList(),
    val pausedAtMs: Long? = null,
    val otherSoundMs: Map<NightSound, Long> = emptyMap(),
    val gapThresholdMs: Long = SessionAccumulator.DEFAULT_GAP_THRESHOLD_MS,
    val algorithmVersion: Int = SessionAccumulator.ALGORITHM_VERSION,
)

/**
 * Streams analysed audio frames into night statistics. Pure and deterministic:
 * all times come from the caller, so it is fully unit-testable.
 */
class SessionAccumulator(initial: AccumulatorState) {

    var state: AccumulatorState = initial
        private set

    val isPaused: Boolean get() = state.pausedAtMs != null

    fun add(frame: FrameEvidence, verdict: FrameVerdict) {
        if (isPaused) return
        val s = state
        val duration = frame.durationMs

        val interruptions = if (frame.timestampMs - s.lastFrameEndMs > s.gapThresholdMs) {
            s.interruptions + TimeSpan(s.lastFrameEndMs, frame.timestampMs)
        } else {
            s.interruptions
        }

        val minuteIndex = ((frame.timestampMs - s.startEpochMs) / 60_000L).toInt().coerceAtLeast(0)
        val bucket = s.minutes[minuteIndex] ?: MinuteBucket()
        val newBucket = bucket.copy(
            frameMs = bucket.frameMs + duration,
            snoreMs = bucket.snoreMs + if (verdict.isSnore) duration else 0L,
            peakIntensity = if (verdict.isSnore) max(bucket.peakIntensity, verdict.intensity) else bucket.peakIntensity,
            maxBand = if (verdict.band.weight > bucket.maxBand.weight) verdict.band else bucket.maxBand,
        )

        val other = frame.otherSound
        state = s.copy(
            lastFrameEndMs = max(s.lastFrameEndMs, frame.timestampMs + duration),
            monitoredMs = s.monitoredMs + duration,
            snoringMs = s.snoringMs + if (verdict.isSnore) duration else 0L,
            bandMs = s.bandMs.plusDuration(verdict.band, duration),
            peakSnoreDbfs = if (verdict.isSnore) max(s.peakSnoreDbfs ?: frame.loudnessDbfs, frame.loudnessDbfs) else s.peakSnoreDbfs,
            minutes = s.minutes + (minuteIndex to newBucket),
            interruptions = interruptions,
            otherSoundMs = if (!verdict.isSnore && other != null) s.otherSoundMs.plusDuration(other, duration) else s.otherSoundMs,
        )
    }

    fun pause(atMs: Long) {
        if (!isPaused) state = state.copy(pausedAtMs = atMs)
    }

    fun resume(atMs: Long) {
        val pausedAt = state.pausedAtMs ?: return
        state = state.copy(
            pauses = state.pauses + TimeSpan(pausedAt, atMs),
            pausedAtMs = null,
            // A pause is not an interruption: restart gap tracking from now.
            lastFrameEndMs = max(state.lastFrameEndMs, atMs),
        )
    }

    fun liveScore(): Int = SnoreScore.compute(state.bandMs, state.monitoredMs)

    fun finish(endEpochMs: Long, reason: EndReason): SessionSummary {
        if (isPaused) resume(endEpochMs)
        val s = state
        val chart = s.minutes.entries.sortedBy { it.key }.map { (minute, b) ->
            ChartPoint(
                minuteIndex = minute,
                snoreFraction = if (b.frameMs == 0L) 0f else (b.snoreMs.toFloat() / b.frameMs).coerceIn(0f, 1f),
                peakIntensity = b.peakIntensity,
                band = b.maxBand,
            )
        }
        return SessionSummary(
            id = s.sessionId,
            startEpochMs = s.startEpochMs,
            endEpochMs = max(endEpochMs, s.startEpochMs),
            monitoredMs = s.monitoredMs,
            snoringMs = s.snoringMs,
            bandMs = s.bandMs,
            snoreScore = SnoreScore.compute(s.bandMs, s.monitoredMs),
            peakSnoreDbfs = s.peakSnoreDbfs,
            chart = chart,
            interruptions = s.interruptions,
            pauses = s.pauses,
            otherSoundMs = s.otherSoundMs,
            endReason = reason,
            algorithmVersion = s.algorithmVersion,
        )
    }

    companion object {
        /** Bump whenever detection or scoring changes, so old nights can be told apart. */
        const val ALGORITHM_VERSION = 1

        /** Missing audio longer than this counts as an interruption. */
        const val DEFAULT_GAP_THRESHOLD_MS = 15_000L

        fun start(sessionId: String, startEpochMs: Long): SessionAccumulator =
            SessionAccumulator(AccumulatorState(sessionId = sessionId, startEpochMs = startEpochMs, lastFrameEndMs = startEpochMs))
    }
}

private fun <K> Map<K, Long>.plusDuration(key: K, ms: Long): Map<K, Long> = this + (key to ((this[key] ?: 0L) + ms))
