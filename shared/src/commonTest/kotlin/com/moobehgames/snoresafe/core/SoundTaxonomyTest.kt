package com.moobehgames.snoresafe.core

import com.moobehgames.snoresafe.core.analysis.NightSound
import com.moobehgames.snoresafe.core.analysis.SoundTaxonomy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class SoundTaxonomyTest {
    private val labels = listOf("Speech", "Snoring", "Snort", "Cough", "Dog", "Silence")
    private val taxonomy = SoundTaxonomy(labels)

    private fun scores(vararg pairs: Pair<String, Float>) =
        FloatArray(labels.size).also { arr -> pairs.forEach { (l, v) -> arr[labels.indexOf(l)] = v } }

    @Test fun snoringScoreIsReported() {
        assertEquals(0.7f, taxonomy.evidence(scores("Snoring" to 0.7f)).snoreProbability)
    }

    @Test fun snortCountsAsWeakerSnore() {
        assertEquals(0.8f * 0.5f, taxonomy.evidence(scores("Snort" to 0.5f)).snoreProbability, 1e-6f)
    }

    @Test fun strongestOtherSoundWins() {
        val e = taxonomy.evidence(scores("Speech" to 0.4f, "Dog" to 0.6f))
        assertEquals(NightSound.PET, e.otherSound)
    }

    @Test fun weakOtherSoundsAreIgnored() {
        assertNull(taxonomy.evidence(scores("Cough" to 0.1f)).otherSound)
    }

    @Test fun modelWithoutSnoringIsRejected() {
        assertFailsWith<IllegalArgumentException> { SoundTaxonomy(listOf("Speech")) }
    }
}
