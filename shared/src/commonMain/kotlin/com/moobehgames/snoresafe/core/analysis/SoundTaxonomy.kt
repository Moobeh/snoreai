package com.moobehgames.snoresafe.core.analysis

import kotlin.math.max

/** What a single classifier pass says about a window of audio. */
data class SoundEvidence(
    /** Probability (0..1) that the window contains snoring. */
    val snoreProbability: Float,
    /** Most likely non-snore sound, or null when nothing stands out. */
    val otherSound: NightSound?,
    /** The classifier's own name for it, e.g. "Car alarm". */
    val otherLabel: String? = null,
    val otherConfidence: Float = 0f,
)

/**
 * Maps the raw class scores of a general sound classifier (YAMNet / AudioSet labels)
 * onto what SnoreSafe cares about. Looks labels up by name, so a model swap only needs
 * a matching label list.
 */
class SoundTaxonomy(
    labels: List<String>,
    private val otherSoundThreshold: Float = DEFAULT_THRESHOLD,
) {
    private val labelNames = labels
    private val index: Map<String, Int> = labels.withIndex().associate { it.value to it.index }

    private val snoring: Int = requireNotNull(index[SNORING]) { "Model labels have no '$SNORING' class" }
    private val snort: Int? = index[SNORT]

    private class Group(val sound: NightSound, val ids: IntArray, val threshold: Float)

    private val groups: List<Group> = OTHER_SOUND_LABELS.mapNotNull { (sound, names) ->
        val ids = names.mapNotNull { index[it] }.toIntArray()
        if (ids.isEmpty()) null else Group(sound, ids, if (sound.notable) NOTABLE_THRESHOLD else otherSoundThreshold)
    }

    /** Labels from [OTHER_SOUND_LABELS] that this model doesn't have. Useful in tests and when swapping models. */
    val missingLabels: List<String> = OTHER_SOUND_LABELS.values.flatten().filter { it !in index }

    fun evidence(scores: FloatArray): SoundEvidence {
        require(scores.size == labelNames.size) { "Expected ${labelNames.size} scores, got ${scores.size}" }
        val snore = max(scores[snoring], snort?.let { scores[it] * SNORT_WEIGHT } ?: 0f).coerceIn(0f, 1f)

        // Compare by how far each score clears its own threshold, so notable sounds can win at lower scores.
        var best: Group? = null
        var bestId = -1
        var bestMargin = Float.NEGATIVE_INFINITY
        for (g in groups) {
            for (id in g.ids) {
                val margin = scores[id] - g.threshold
                if (margin >= 0f && margin > bestMargin) {
                    best = g
                    bestId = id
                    bestMargin = margin
                }
            }
        }
        return if (best == null) {
            SoundEvidence(snore, null)
        } else {
            SoundEvidence(snore, best.sound, labelNames[bestId], scores[bestId])
        }
    }

    companion object {
        const val SNORING = "Snoring"
        const val SNORT = "Snort"
        const val DEFAULT_THRESHOLD = 0.35f
        const val NOTABLE_THRESHOLD = 0.25f
        private const val SNORT_WEIGHT = 0.8f

        val OTHER_SOUND_LABELS: Map<NightSound, List<String>> = mapOf(
            NightSound.SPEECH to listOf("Speech", "Conversation", "Whispering", "Narration, monologue"),
            NightSound.SHOUTING to listOf("Shout", "Bellow", "Yell", "Screaming", "Children shouting"),
            NightSound.LAUGHTER to listOf("Laughter", "Baby laughter", "Belly laugh"),
            NightSound.CRYING to listOf("Crying, sobbing", "Whimper"),
            NightSound.BABY to listOf("Baby cry, infant cry"),
            NightSound.COUGH to listOf("Cough", "Throat clearing", "Sneeze", "Sniff"),
            NightSound.BREATHING to listOf("Breathing", "Gasp", "Pant", "Wheeze"),
            NightSound.BODY to listOf("Hiccup", "Burping, eructation", "Fart", "Chewing, mastication"),
            NightSound.PET to listOf("Dog", "Bark", "Howl", "Growling", "Whimper (dog)", "Cat", "Meow", "Purr"),
            NightSound.BIRDS to listOf("Bird", "Bird vocalization, bird call, bird song", "Owl"),
            NightSound.INSECTS to listOf("Insect", "Cricket", "Mosquito", "Buzz"),
            NightSound.FAN_OR_AC to listOf("Mechanical fan", "Air conditioning"),
            NightSound.APPLIANCE to listOf("Vacuum cleaner", "Microwave oven", "Blender"),
            NightSound.TRAFFIC to listOf("Vehicle", "Traffic noise, roadway noise", "Car", "Motorcycle", "Truck", "Air horn, truck horn", "Reversing beeps"),
            NightSound.EMERGENCY_SIREN to listOf("Siren", "Police car (siren)", "Ambulance (siren)", "Fire engine, fire truck (siren)", "Civil defense siren"),
            NightSound.CAR_ALARM to listOf("Car alarm"),
            NightSound.SMOKE_ALARM to listOf("Smoke detector, smoke alarm", "Fire alarm"),
            NightSound.ALARM to listOf("Alarm", "Alarm clock", "Buzzer"),
            NightSound.PHONE to listOf("Telephone bell ringing", "Ringtone"),
            NightSound.DOORBELL to listOf("Doorbell", "Knock"),
            NightSound.DOOR to listOf("Door", "Sliding door", "Slam"),
            NightSound.FOOTSTEPS to listOf("Walk, footsteps"),
            NightSound.WATER to listOf("Water tap, faucet", "Sink (filling or washing)", "Bathtub (filling or washing)", "Toilet flush", "Drip"),
            NightSound.GLASS to listOf("Glass", "Shatter"),
            NightSound.BANG to listOf("Bang", "Explosion", "Gunshot, gunfire", "Fireworks", "Firecracker"),
            NightSound.AIRCRAFT to listOf("Aircraft", "Aircraft engine", "Fixed-wing aircraft, airplane"),
            NightSound.TRAIN to listOf("Train", "Train whistle", "Train horn", "Railroad car, train wagon", "Train wheels squealing"),
            NightSound.MEDIA to listOf("Music", "Television", "Radio"),
            NightSound.RAIN to listOf("Rain", "Raindrop", "Rain on surface"),
            NightSound.THUNDER to listOf("Thunder", "Thunderstorm"),
            NightSound.WIND to listOf("Wind"),
        )
    }
}
