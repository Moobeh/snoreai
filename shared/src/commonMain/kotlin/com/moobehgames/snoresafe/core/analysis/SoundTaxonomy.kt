package com.moobehgames.snoresafe.core.analysis

import kotlin.math.max

/** What a single classifier pass says about a window of audio. */
data class SoundEvidence(
    /** Probability (0..1) that the window contains snoring. */
    val snoreProbability: Float,
    /** Most likely non-snore sound, or null when nothing stands out. */
    val otherSound: NightSound?,
)

/**
 * Maps the raw class scores of a general sound classifier (YAMNet / AudioSet labels)
 * onto what SnoreSafe cares about. Looks labels up by name, so a model swap only needs
 * a matching label list.
 */
class SoundTaxonomy(labels: List<String>, private val otherSoundThreshold: Float = 0.3f) {

    private val index: Map<String, Int> = labels.withIndex().associate { it.value to it.index }
    private val labelCount = labels.size

    private val snoring: Int = requireNotNull(index[SNORING]) { "Model labels have no '$SNORING' class" }
    private val snort: Int? = index[SNORT]

    private val groups: List<Pair<NightSound, IntArray>> = OTHER_SOUND_LABELS.map { (sound, names) ->
        sound to names.mapNotNull { index[it] }.toIntArray()
    }.filter { it.second.isNotEmpty() }

    fun evidence(scores: FloatArray): SoundEvidence {
        require(scores.size == labelCount) { "Expected $labelCount scores, got ${scores.size}" }
        val snore = max(scores[snoring], snort?.let { scores[it] * SNORT_WEIGHT } ?: 0f).coerceIn(0f, 1f)

        var best: NightSound? = null
        var bestScore = otherSoundThreshold
        for ((sound, ids) in groups) {
            var v = 0f
            for (id in ids) v = max(v, scores[id])
            if (v > bestScore) {
                best = sound
                bestScore = v
            }
        }
        return SoundEvidence(snore, best)
    }

    companion object {
        const val SNORING = "Snoring"
        const val SNORT = "Snort"
        private const val SNORT_WEIGHT = 0.8f

        val OTHER_SOUND_LABELS: Map<NightSound, List<String>> = mapOf(
            NightSound.SPEECH to listOf("Speech", "Conversation", "Whispering", "Narration, monologue"),
            NightSound.COUGH to listOf("Cough", "Throat clearing", "Sneeze"),
            NightSound.BREATHING to listOf("Breathing", "Gasp", "Pant", "Wheeze"),
            NightSound.PET to listOf("Dog", "Bark", "Cat", "Meow", "Purr"),
            NightSound.FAN_OR_AC to listOf("Mechanical fan", "Air conditioning"),
            NightSound.TRAFFIC to listOf("Vehicle", "Traffic noise, roadway noise", "Car", "Motorcycle"),
            NightSound.MEDIA to listOf("Music", "Television", "Radio"),
            NightSound.RAIN to listOf("Rain", "Raindrop", "Rain on surface", "Thunder"),
            NightSound.BABY to listOf("Baby cry, infant cry"),
        )
    }
}
