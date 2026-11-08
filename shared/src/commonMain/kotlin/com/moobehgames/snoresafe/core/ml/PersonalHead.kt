package com.moobehgames.snoresafe.core.ml

import kotlinx.serialization.Serializable
import kotlin.math.exp
import kotlin.math.min
import kotlin.random.Random

/** A user's clip label: the classifier's class scores for that clip plus what the user said it was. */
class LabeledExample(val features: FloatArray, val isSnore: Boolean)

@Serializable
data class PersonalHeadWeights(
    val weights: List<Float>,
    val bias: Float,
    val trainedOn: Int,
)

/**
 * Personal snore model. A small logistic-regression layer trained **on the phone**
 * on top of the base classifier's scores, from the user's "snoring / not snoring" labels.
 * It learns this bedroom: this snore, this partner, this fan, this dog.
 */
object PersonalHead {
    const val MIN_EXAMPLES_PER_CLASS = 5

    /** Labels needed before the personal model gets its full say. */
    const val FULL_TRUST_EXAMPLES = 50
    private const val MAX_PERSONAL_WEIGHT = 0.8f

    fun canTrain(examples: List<LabeledExample>): Boolean =
        examples.count { it.isSnore } >= MIN_EXAMPLES_PER_CLASS &&
            examples.count { !it.isSnore } >= MIN_EXAMPLES_PER_CLASS

    /** Returns null when there are not yet enough labels of both kinds. */
    fun train(
        examples: List<LabeledExample>,
        epochs: Int = 200,
        learningRate: Float = 0.1f,
        l2: Float = 1e-3f,
        seed: Int = 7,
    ): PersonalHeadWeights? {
        if (!canTrain(examples)) return null
        val size = examples.first().features.size
        require(examples.all { it.features.size == size }) { "All examples must have the same feature size" }

        // Balance classes so a pile of "not snoring" labels doesn't drown the snores.
        val positives = examples.count { it.isSnore }
        val negatives = examples.size - positives
        val posWeight = examples.size / (2f * positives)
        val negWeight = examples.size / (2f * negatives)

        val w = FloatArray(size)
        var b = 0f
        val order = examples.indices.toMutableList()
        val random = Random(seed)

        repeat(epochs) {
            order.shuffle(random)
            for (i in order) {
                val ex = examples[i]
                val err = (sigmoid(dot(w, ex.features) + b) - if (ex.isSnore) 1f else 0f) *
                    if (ex.isSnore) posWeight else negWeight
                for (j in 0 until size) {
                    w[j] -= learningRate * (err * ex.features[j] + l2 * w[j])
                }
                b -= learningRate * err
            }
        }
        return PersonalHeadWeights(w.toList(), b, examples.size)
    }

    fun predict(weights: PersonalHeadWeights, features: FloatArray): Float {
        require(features.size == weights.weights.size) { "Feature size mismatch" }
        var z = weights.bias
        for (j in features.indices) z += weights.weights[j] * features[j]
        return sigmoid(z)
    }

    /** Blends base and personal probabilities; the personal model earns trust as labels grow. */
    fun blend(baseProbability: Float, personalProbability: Float, trainedOn: Int): Float {
        val w = min(MAX_PERSONAL_WEIGHT, MAX_PERSONAL_WEIGHT * trainedOn / FULL_TRUST_EXAMPLES)
        return (baseProbability * (1f - w) + personalProbability * w).coerceIn(0f, 1f)
    }

    private fun dot(w: FloatArray, x: FloatArray): Float {
        var s = 0f
        for (i in w.indices) s += w[i] * x[i]
        return s
    }

    private fun sigmoid(z: Float): Float = (1.0 / (1.0 + exp(-z.toDouble()))).toFloat()
}
