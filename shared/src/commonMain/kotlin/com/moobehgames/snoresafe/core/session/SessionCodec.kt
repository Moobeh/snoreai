package com.moobehgames.snoresafe.core.session

import com.moobehgames.snoresafe.core.audio.SessionAudio
import com.moobehgames.snoresafe.core.ml.PersonalHeadWeights
import com.moobehgames.snoresafe.core.settings.AppSettings
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

    fun encodeAudio(audio: SessionAudio): String = json.encodeToString(SessionAudio.serializer(), audio)
    fun decodeAudio(text: String): SessionAudio = json.decodeFromString(SessionAudio.serializer(), text)

    fun encodeSettings(settings: AppSettings): String = json.encodeToString(AppSettings.serializer(), settings)

    /** Falls back to defaults rather than failing, so a bad settings file never blocks the app. */
    fun decodeSettings(text: String): AppSettings = runCatching { json.decodeFromString(AppSettings.serializer(), text) }.getOrElse { AppSettings() }

    fun encodeSummaries(list: List<SessionSummary>): String =
        json.encodeToString(kotlinx.serialization.builtins.ListSerializer(SessionSummary.serializer()), list)

    fun encodeHead(weights: PersonalHeadWeights): String = json.encodeToString(PersonalHeadWeights.serializer(), weights)
    fun decodeHead(text: String): PersonalHeadWeights = json.decodeFromString(PersonalHeadWeights.serializer(), text)
}
