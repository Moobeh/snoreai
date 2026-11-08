package com.moobehgames.snoresafe.core.analysis

import kotlinx.serialization.Serializable

/** How loud a snore was. QUIET means "not snoring". */
@Serializable
enum class SnoreBand(val weight: Int) {
    QUIET(0),
    LIGHT(1),
    LOUD(2),
    EPIC(3);

    companion object {
        const val LOUD_FROM = 0.4f
        const val EPIC_FROM = 0.7f

        fun of(intensity: Float, isSnore: Boolean): SnoreBand = when {
            !isSnore -> QUIET
            intensity < LOUD_FROM -> LIGHT
            intensity < EPIC_FROM -> LOUD
            else -> EPIC
        }
    }
}

@Serializable
data class DetectionSettings(
    /** 0 = only obvious snoring, 1 = catch everything. */
    val sensitivity: Float = 0.5f,
    /** Shifts all levels up or down, for phones with quiet or hot microphones. */
    val volumeOffsetDb: Float = 0f,
    /** Level that maps to intensity 0. */
    val quietFloorDbfs: Float = -60f,
    /** Level that maps to intensity 1. */
    val loudCeilingDbfs: Float = -20f,
    /** Anything quieter than this is never counted as snoring. */
    val minSnoreDbfs: Float = -58f,
    /** Off = "light-snoring filter": light snoring and heavy breathing are not counted. */
    val countLightSnoring: Boolean = true,
) {
    init {
        require(sensitivity in 0f..1f) { "sensitivity must be in 0..1" }
        require(loudCeilingDbfs > quietFloorDbfs) { "ceiling must be above floor" }
    }

    /** Classifier probability needed to call a window snoring: 0.8 at sensitivity 0, 0.2 at 1. */
    val probabilityThreshold: Float get() = 0.8f - 0.6f * sensitivity
}

/** One analysed slice of audio. */
data class FrameEvidence(
    val timestampMs: Long,
    val durationMs: Long,
    val loudnessDbfs: Float,
    val snoreProbability: Float,
    val otherSound: NightSound? = null,
    val otherLabel: String? = null,
    val otherConfidence: Float = 0f,
)

data class FrameVerdict(
    val isSnore: Boolean,
    /** 0..1, calibrated loudness. */
    val intensity: Float,
    val band: SnoreBand,
)

class SnoreDetector(private val settings: DetectionSettings = DetectionSettings()) {

    fun intensityOf(loudnessDbfs: Float): Float {
        val db = loudnessDbfs + settings.volumeOffsetDb
        return ((db - settings.quietFloorDbfs) / (settings.loudCeilingDbfs - settings.quietFloorDbfs)).coerceIn(0f, 1f)
    }

    fun assess(frame: FrameEvidence): FrameVerdict {
        val intensity = intensityOf(frame.loudnessDbfs)
        val loudEnough = frame.loudnessDbfs + settings.volumeOffsetDb >= settings.minSnoreDbfs
        val heard = loudEnough && frame.snoreProbability >= settings.probabilityThreshold
        val band = SnoreBand.of(intensity, heard)
        val isSnore = heard && (settings.countLightSnoring || band != SnoreBand.LIGHT)
        return FrameVerdict(isSnore, intensity, if (isSnore) band else SnoreBand.QUIET)
    }
}
