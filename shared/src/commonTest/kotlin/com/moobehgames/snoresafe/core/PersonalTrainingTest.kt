package com.moobehgames.snoresafe.core

import com.moobehgames.snoresafe.core.audio.AudioClip
import com.moobehgames.snoresafe.core.audio.ClipKind
import com.moobehgames.snoresafe.core.audio.ClipLabel
import com.moobehgames.snoresafe.core.audio.SessionAudio
import com.moobehgames.snoresafe.core.ml.PersonalHead
import com.moobehgames.snoresafe.core.ml.PersonalTraining
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PersonalTrainingTest {
    private fun clip(id: String, label: ClipLabel?, features: List<Float>?) =
        AudioClip(id, ClipKind.SNORE, 0, 1000, "$id.m4a", 1, 0.5f, features = features, userLabel = label)

    @Test fun onlyLabelledClipsWithFeaturesBecomeExamples() {
        val night = SessionAudio(
            "n",
            listOf(
                clip("a", ClipLabel.SNORING, listOf(0.9f, 0.1f)),
                clip("b", ClipLabel.NOT_SNORING, listOf(0.6f, 0.9f)),
                clip("c", null, listOf(0.5f, 0.5f)),
                clip("d", ClipLabel.SNORING, null),
                clip("e", ClipLabel.SNORING, listOf(0.1f)), // different model size: ignored
            ),
        )
        val ex = PersonalTraining.examples(listOf(night))
        assertEquals(2, ex.size)
        assertEquals(listOf(true, false), ex.map { it.isSnore })
        val counts = PersonalTraining.countsInAudio(listOf(night))
        assertEquals(3, counts.snoring)
        assertEquals(1, counts.notSnoring)
        assertFalse(counts.enoughToTrain)
    }

    @Test fun trainedModelCorrectsTheBaseOnTheUsersFan() {
        // The base model thinks the fan is snoring (feature 0 high); feature 1 is the fan.
        val clips = (0 until 20).map { clip("s$it", ClipLabel.SNORING, listOf(0.8f, 0.05f)) } +
            (0 until 20).map { clip("f$it", ClipLabel.NOT_SNORING, listOf(0.7f, 0.9f)) }
        val weights = assertNotNull(PersonalHead.train(PersonalTraining.examples(listOf(SessionAudio("n", clips)))))
        val fan = PersonalTraining.snoreProbability(base = 0.7f, scores = floatArrayOf(0.7f, 0.9f), weights = weights)
        val snore = PersonalTraining.snoreProbability(base = 0.8f, scores = floatArrayOf(0.8f, 0.05f), weights = weights)
        assertTrue(fan < 0.5f, "fan should drop below the default threshold, was $fan")
        assertTrue(snore > 0.5f, "snoring should stay above it, was $snore")
        assertEquals(0.7f, PersonalTraining.snoreProbability(0.7f, floatArrayOf(1f, 2f, 3f), weights)) // size mismatch → base
    }
}

class TrainingSetTest {
    private fun clip(id: String, features: List<Float>?) =
        com.moobehgames.snoresafe.core.audio.AudioClip(id, com.moobehgames.snoresafe.core.audio.ClipKind.SNORE, 0, 1000, "$id.m4a", 1, 0.5f, features = features)

    @Test fun labelsAreAddedReplacedAndRemoved() {
        var set = emptyList<com.moobehgames.snoresafe.core.ml.TrainingExample>()
        set = PersonalTraining.update(set, clip("a", listOf(1f, 0f)), ClipLabel.SNORING)
        set = PersonalTraining.update(set, clip("b", listOf(0f, 1f)), ClipLabel.NOT_SNORING)
        set = PersonalTraining.update(set, clip("a", listOf(1f, 0f)), ClipLabel.NOT_SNORING) // changed mind
        assertEquals(PersonalTraining.LabelCounts(0, 2), PersonalTraining.counts(set))
        set = PersonalTraining.update(set, clip("b", listOf(0f, 1f)), null)
        assertEquals(listOf("a"), set.map { it.clipId })
        assertEquals(set, PersonalTraining.update(set, clip("c", null), ClipLabel.SNORING)) // no features, nothing learned
        assertEquals(set, com.moobehgames.snoresafe.core.session.SessionCodec.decodeTrainingSet(com.moobehgames.snoresafe.core.session.SessionCodec.encodeTrainingSet(set)))
    }
}
