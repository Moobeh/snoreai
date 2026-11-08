package com.moobehgames.snoresafe.core.session

import com.moobehgames.snoresafe.core.ml.PersonalHeadWeights
import kotlinx.serialization.json.Json

/** JSON encoding for everything SnoreSafe stores on disk. */
object SessionCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encodeSummary(summary: SessionSummary): String = json.encodeToString(SessionSummary.serializer(), summary)
    fun decodeSummary(text: String): SessionSummary = json.decodeFromString(SessionSummary.serializer(), text)

    fun encodeCheckpoint(state: AccumulatorState): String = json.encodeToString(AccumulatorState.serializer(), state)
    fun decodeCheckpoint(text: String): AccumulatorState = json.decodeFromString(AccumulatorState.serializer(), text)

    fun encodeHead(weights: PersonalHeadWeights): String = json.encodeToString(PersonalHeadWeights.serializer(), weights)
    fun decodeHead(text: String): PersonalHeadWeights = json.decodeFromString(PersonalHeadWeights.serializer(), text)
}
