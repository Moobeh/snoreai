package com.moobehgames.snoresafe.core.analysis

import kotlin.math.log10
import kotlin.math.sqrt

/** Loudness helpers for normalised PCM samples in the range [-1, 1]. */
object Loudness {
    /** Level reported for digital silence. */
    const val SILENCE_DBFS: Float = -120f

    fun rms(samples: FloatArray, offset: Int = 0, length: Int = samples.size - offset): Float {
        require(offset >= 0 && length >= 0 && offset + length <= samples.size) { "Range out of bounds" }
        if (length == 0) return 0f
        var sum = 0.0
        for (i in offset until offset + length) {
            val s = samples[i].toDouble()
            sum += s * s
        }
        return sqrt(sum / length).toFloat()
    }

    /** Decibels relative to full scale. 0 dBFS = loudest possible signal. */
    fun dbfs(rms: Float): Float =
        if (rms <= 1e-6f) SILENCE_DBFS else (20.0 * log10(rms.toDouble())).toFloat().coerceAtLeast(SILENCE_DBFS)
}
