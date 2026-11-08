package com.moobehgames.snoresafe.core.ml

import com.moobehgames.snoresafe.core.audio.AudioClip
import com.moobehgames.snoresafe.core.audio.ClipLabel
import com.moobehgames.snoresafe.core.audio.SessionAudio
import kotlinx.serialization.Serializable

/** A label kept independently of its recording, so cleaning up old audio never erases what the model learned. */
@Serializable
data class TrainingExample(val clipId: String, val isSnore: Boolean, val features: List<Float>)

/** Turns the user's labelled recordings into training data for [PersonalHead]. */
object PersonalTraining {

    data class LabelCounts(val snoring: Int, val notSnoring: Int) {
        val total: Int get() = snoring + notSnoring
        val enoughToTrain: Boolean get() = snoring >= PersonalHead.MIN_EXAMPLES_PER_CLASS && notSnoring >= PersonalHead.MIN_EXAMPLES_PER_CLASS
    }

    fun examples(nights: List<SessionAudio>): List<LabeledExample> {
        val labelled = nights.flatMap { it.clips }.filter { it.userLabel != null && !it.features.isNullOrEmpty() }
        // Only use clips from the same model (same number of scores), in case the model ever changes.
        val size = labelled.groupingBy { it.features!!.size }.eachCount().maxByOrNull { it.value }?.key ?: return emptyList()
        return labelled.filter { it.features!!.size == size }
            .map { LabeledExample(it.features!!.toFloatArray(), it.userLabel == ClipLabel.SNORING) }
    }

    /** Adds, replaces or (label = null) removes the example for [clip]. Clips without features can't teach anything. */
    fun update(set: List<TrainingExample>, clip: AudioClip, label: ClipLabel?): List<TrainingExample> {
        val rest = set.filterNot { it.clipId == clip.id }
        val features = clip.features
        if (label == null || features.isNullOrEmpty()) return rest
        return (rest + TrainingExample(clip.id, label == ClipLabel.SNORING, features)).takeLast(MAX_EXAMPLES)
    }

    fun fromSet(set: List<TrainingExample>): List<LabeledExample> {
        val size = set.groupingBy { it.features.size }.eachCount().maxByOrNull { it.value }?.key ?: return emptyList()
        return set.filter { it.features.size == size }.map { LabeledExample(it.features.toFloatArray(), it.isSnore) }
    }

    fun counts(set: List<TrainingExample>): LabelCounts = LabelCounts(set.count { it.isSnore }, set.count { !it.isSnore })

    /** Keeps training fast and the file small; the newest labels win. */
    const val MAX_EXAMPLES = 2_000

    fun countsInAudio(nights: List<SessionAudio>): LabelCounts {
        val labels = nights.flatMap { it.clips }.mapNotNull { it.userLabel }
        return LabelCounts(labels.count { it == ClipLabel.SNORING }, labels.count { it == ClipLabel.NOT_SNORING })
    }

    /** Snore probability for one frame: the base model's, adjusted by the personal model once it has been trained. */
    fun snoreProbability(base: Float, scores: FloatArray, weights: PersonalHeadWeights?): Float {
        if (weights == null || weights.weights.size != scores.size) return base
        return PersonalHead.blend(base, PersonalHead.predict(weights, scores), weights.trainedOn)
    }
}
