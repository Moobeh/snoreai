package com.moobehgames.snoresafe.core.session

import com.moobehgames.snoresafe.core.audio.SessionAudio
import com.moobehgames.snoresafe.core.ml.PersonalHeadWeights
import com.moobehgames.snoresafe.core.settings.AppSettings
import com.moobehgames.snoresafe.core.tags.NightLog
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

    fun encodeCustomTags(tags: List<com.moobehgames.snoresafe.core.tags.CustomTag>): String =
        json.encodeToString(kotlinx.serialization.builtins.ListSerializer(com.moobehgames.snoresafe.core.tags.CustomTag.serializer()), tags)
    fun decodeCustomTags(text: String): List<com.moobehgames.snoresafe.core.tags.CustomTag> =
        json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(com.moobehgames.snoresafe.core.tags.CustomTag.serializer()), text)

    fun encodeLog(log: NightLog): String = json.encodeToString(NightLog.serializer(), log)
    fun decodeLog(text: String): NightLog = json.decodeFromString(NightLog.serializer(), text)

    fun encodeSettings(settings: AppSettings): String = json.encodeToString(AppSettings.serializer(), settings)

    /** Falls back to defaults rather than failing, so a bad settings file never blocks the app. */
    fun decodeSettings(text: String): AppSettings = runCatching { json.decodeFromString(AppSettings.serializer(), text) }.getOrElse { AppSettings() }

    fun encodeSummaries(list: List<SessionSummary>): String =
        json.encodeToString(kotlinx.serialization.builtins.ListSerializer(SessionSummary.serializer()), list)

    fun encodeHead(weights: PersonalHeadWeights): String = json.encodeToString(PersonalHeadWeights.serializer(), weights)
    fun decodeHead(text: String): PersonalHeadWeights = json.decodeFromString(PersonalHeadWeights.serializer(), text)
}
