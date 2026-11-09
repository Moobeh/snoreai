package com.moobehgames.snoresafe.core

import com.moobehgames.snoresafe.core.analysis.NightSound
import com.moobehgames.snoresafe.core.analysis.SoundTaxonomy
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Guards the mapping against typos: every label we map must exist in the model shipped in the app. */
class BundledModelLabelsTest {
    private val labels = File("../androidApp/src/main/assets/yamnet_labels.txt").readLines().map { it.trim() }.filter { it.isNotEmpty() }

    @Test fun everyMappedLabelExistsInTheShippedModel() {
        assertEquals(521, labels.size)
        assertEquals(emptyList(), SoundTaxonomy(labels).missingLabels)
    }

    @Test fun everySoundCategoryIsMapped() {
        assertTrue(NightSound.entries.all { it in SoundTaxonomy.OTHER_SOUND_LABELS })
    }
}
