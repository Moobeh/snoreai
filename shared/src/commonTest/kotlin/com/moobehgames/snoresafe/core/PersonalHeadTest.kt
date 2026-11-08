package com.moobehgames.snoresafe.core

import com.moobehgames.snoresafe.core.ml.LabeledExample
import com.moobehgames.snoresafe.core.ml.PersonalHead
import com.moobehgames.snoresafe.core.session.SessionCodec
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PersonalHeadTest {
    private val random = Random(1)

    /** Feature 0 = base "snoring" score; feature 1 = "fan" score. The user's fan fools the base model. */
    private fun snore() = LabeledExample(floatArrayOf(0.6f + random.nextFloat() * 0.3f, random.nextFloat() * 0.2f), true)
    private fun fan() = LabeledExample(floatArrayOf(0.5f + random.nextFloat() * 0.3f, 0.7f + random.nextFloat() * 0.3f), false)

    @Test fun needsBothKindsOfLabels() {
        assertNull(PersonalHead.train(List(20) { snore() }))
    }

    @Test fun learnsToRejectTheUsersFan() {
        val data = List(30) { snore() } + List(30) { fan() }
        val head = assertNotNull(PersonalHead.train(data))
        repeat(20) {
            assertTrue(PersonalHead.predict(head, snore().features) > 0.5f)
            assertTrue(PersonalHead.predict(head, fan().features) < 0.5f)
        }
        assertEquals(head, SessionCodec.decodeHead(SessionCodec.encodeHead(head)))
    }

    @Test fun blendTrustGrowsWithLabels() {
        assertEquals(0.9f, PersonalHead.blend(0.9f, 0.1f, trainedOn = 0), 1e-6f)
        val few = PersonalHead.blend(0.9f, 0.1f, trainedOn = 10)
        val many = PersonalHead.blend(0.9f, 0.1f, trainedOn = 500)
        assertTrue(many < few && few < 0.9f)
        assertEquals(0.9f * 0.2f + 0.1f * 0.8f, many, 1e-6f)
    }
}
