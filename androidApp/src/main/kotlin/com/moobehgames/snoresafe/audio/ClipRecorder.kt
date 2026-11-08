package com.moobehgames.snoresafe.audio

import android.util.Log
import com.moobehgames.snoresafe.core.analysis.FrameEvidence
import com.moobehgames.snoresafe.core.analysis.FrameVerdict
import com.moobehgames.snoresafe.core.audio.AudioClip
import com.moobehgames.snoresafe.core.audio.ClipKind
import com.moobehgames.snoresafe.core.settings.RecordingMode
import com.moobehgames.snoresafe.data.AudioStore
import java.io.File
import java.util.UUID
import kotlin.math.max

/**
 * Turns the analysed audio stream into saved clips.
 *
 * - Event clips: start when snoring (or another sound) is heard, include ~3 s of audio from
 *   just before it, and end after 2 s of quiet or 30 s.
 * - Full-night mode also writes the whole night in 10-minute segments.
 *
 * Every finished clip is added to the night's index straight away.
 */
class ClipRecorder(
    private val store: AudioStore,
    private val sessionId: String,
    private val sampleRate: Int,
    private val hopSamples: Int,
    private val mode: RecordingMode,
    private val saveOtherSounds: Boolean,
    private val freeBytes: () -> Long,
) {
    private val hopMs = hopSamples * 1000L / sampleRate
    private val preRoll = ArrayDeque<FloatArray>()
    private val preRollHops = (PRE_ROLL_MS / hopMs).toInt().coerceAtLeast(1)

    private var clip: OpenClip? = null
    private var segment: OpenSegment? = null
    private var outOfSpace = false

    private class OpenClip(
        val id: String,
        val kind: ClipKind,
        val file: File,
        val writer: AacFileWriter,
        val startMs: Long,
        var lastActiveMs: Long,
        var peak: Float,
        val evidence: FrameEvidence,
        val featureSum: FloatArray,
        var featureCount: Int,
    )

    private class OpenSegment(val id: String, val file: File, val writer: AacFileWriter, val startMs: Long)

    /** [hop] = the newest hop of audio; [frameEndMs] = when it ended. */
    fun onHop(hop: FloatArray, frameEndMs: Long, frame: FrameEvidence, verdict: FrameVerdict, scores: FloatArray?) {
        if (!outOfSpace && freeBytes() < MIN_FREE_BYTES) {
            Log.w(TAG, "Low storage: no more audio saved tonight")
            outOfSpace = true
            closeAll(frameEndMs)
        }
        if (outOfSpace) return
        val hopStartMs = frameEndMs - hopMs

        if (mode == RecordingMode.FULL_NIGHT) writeSegment(hop, hopStartMs, frameEndMs)

        val active = verdict.isSnore || (saveOtherSounds && frame.otherSound != null)
        val c = clip
        if (c == null) {
            if (active) clip = startClip(frame, verdict, hop, hopStartMs, scores)
        } else {
            safeWrite(c.writer, hop)
            if (active) c.lastActiveMs = frameEndMs
            c.peak = max(c.peak, if (c.kind == ClipKind.SNORE) (if (verdict.isSnore) verdict.intensity else 0f) else frame.otherConfidence)
            scores?.let { addFeatures(c, it) }
            if (frameEndMs - c.lastActiveMs >= TAIL_MS || frameEndMs - c.startMs >= MAX_CLIP_MS) finishClip(frameEndMs)
        }

        preRoll.addLast(hop.copyOf())
        while (preRoll.size > preRollHops) preRoll.removeFirst()
    }

    fun closeAll(endMs: Long) {
        clip?.let { finishClip(endMs) }
        segment?.let { finishSegment(endMs) }
    }

    private fun startClip(frame: FrameEvidence, verdict: FrameVerdict, hop: FloatArray, hopStartMs: Long, scores: FloatArray?): OpenClip? {
        val id = UUID.randomUUID().toString()
        val file = File(store.dirFor(sessionId), "$id.m4a")
        val writer = runCatching { AacFileWriter(file, sampleRate) }.getOrElse {
            Log.w(TAG, "Encoder unavailable", it)
            return null
        }
        val preRollMs = preRoll.size * hopMs
        preRoll.forEach { safeWrite(writer, it) }
        safeWrite(writer, hop)
        val kind = if (verdict.isSnore) ClipKind.SNORE else ClipKind.SOUND
        return OpenClip(
            id, kind, file, writer,
            startMs = hopStartMs - preRollMs,
            lastActiveMs = hopStartMs + hopMs,
            peak = if (kind == ClipKind.SNORE) verdict.intensity else frame.otherConfidence,
            evidence = frame,
            featureSum = FloatArray(scores?.size ?: 0),
            featureCount = 0,
        ).also { c -> scores?.let { addFeatures(c, it) } }
    }

    private fun finishClip(endMs: Long) {
        val c = clip ?: return
        clip = null
        val ok = runCatching { c.writer.finish() }.getOrDefault(false)
        if (!ok) return
        val features = if (c.featureCount > 0) c.featureSum.map { it / c.featureCount } else null
        store.addClip(
            sessionId,
            AudioClip(
                id = c.id,
                kind = c.kind,
                startEpochMs = c.startMs,
                durationMs = c.writer.durationMs,
                fileName = c.file.name,
                sizeBytes = c.file.length(),
                peak = c.peak,
                sound = if (c.kind == ClipKind.SOUND) c.evidence.otherSound else null,
                label = if (c.kind == ClipKind.SOUND) c.evidence.otherLabel else null,
                features = features,
            ),
        )
        if (endMs - c.startMs < 0) Log.w(TAG, "Clip ended before it started")
    }

    private fun writeSegment(hop: FloatArray, hopStartMs: Long, hopEndMs: Long) {
        var s = segment
        if (s != null && hopStartMs - s.startMs >= SEGMENT_MS) {
            finishSegment(hopStartMs)
            s = null
        }
        if (s == null) {
            val id = UUID.randomUUID().toString()
            val file = File(store.dirFor(sessionId), "night-$id.m4a")
            val writer = runCatching { AacFileWriter(file, sampleRate) }.getOrNull() ?: return
            s = OpenSegment(id, file, writer, hopStartMs)
            segment = s
        }
        safeWrite(s.writer, hop)
        if (hopEndMs < hopStartMs) Log.w(TAG, "Clock went backwards")
    }

    private fun finishSegment(endMs: Long) {
        val s = segment ?: return
        segment = null
        if (!runCatching { s.writer.finish() }.getOrDefault(false)) return
        store.addClip(
            sessionId,
            AudioClip(s.id, ClipKind.SEGMENT, s.startMs, s.writer.durationMs, s.file.name, s.file.length(), peak = 0f),
        )
        if (endMs < s.startMs) Log.w(TAG, "Segment ended before it started")
    }

    private fun addFeatures(c: OpenClip, scores: FloatArray) {
        if (scores.size != c.featureSum.size) return
        for (i in scores.indices) c.featureSum[i] += scores[i]
        c.featureCount++
    }

    private fun safeWrite(writer: AacFileWriter, samples: FloatArray) {
        runCatching { writer.write(samples) }.onFailure { Log.w(TAG, "Audio write failed", it) }
    }

    companion object {
        private const val TAG = "ClipRecorder"
        const val PRE_ROLL_MS = 3_000L
        const val TAIL_MS = 2_000L
        const val MAX_CLIP_MS = 30_000L
        const val SEGMENT_MS = 10L * 60 * 1000
        const val MIN_FREE_BYTES = 200L * 1024 * 1024
    }
}
