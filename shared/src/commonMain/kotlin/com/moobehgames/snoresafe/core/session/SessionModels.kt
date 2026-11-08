package com.moobehgames.snoresafe.core.session

import com.moobehgames.snoresafe.core.analysis.NightSound
import com.moobehgames.snoresafe.core.analysis.SnoreBand
import com.moobehgames.snoresafe.core.analysis.SoundEvent
import com.moobehgames.snoresafe.core.settings.RecordingMode
import kotlinx.serialization.Serializable

/** A time range, e.g. a pause or an interruption. */
@Serializable
data class TimeSpan(val startEpochMs: Long, val endEpochMs: Long) {
    val durationMs: Long get() = endEpochMs - startEpochMs
}

/** One minute of the night, for the timeline chart. */
@Serializable
data class ChartPoint(
    val minuteIndex: Int,
    val snoreFraction: Float,
    val peakIntensity: Float,
    val band: SnoreBand,
)

@Serializable
enum class EndReason {
    /** The user stopped the session. */
    USER,

    /** The app was killed overnight; the session was rebuilt from its last checkpoint. */
    RECOVERED,
}

@Serializable
data class SessionSummary(
    val id: String,
    val startEpochMs: Long,
    val endEpochMs: Long,
    val monitoredMs: Long,
    val snoringMs: Long,
    val bandMs: Map<SnoreBand, Long>,
    val snoreScore: Int,
    val peakSnoreDbfs: Float?,
    val chart: List<ChartPoint>,
    val interruptions: List<TimeSpan>,
    val pauses: List<TimeSpan>,
    val otherSoundMs: Map<NightSound, Long>,
    val soundEvents: List<SoundEvent> = emptyList(),
    val recordingMode: RecordingMode = RecordingMode.DEFAULT,
    val endReason: EndReason,
    val algorithmVersion: Int,
) {
    /** Events per sound, loudest-confidence first within each. */
    val soundEventsBySound: Map<NightSound, List<SoundEvent>>
        get() = soundEvents.groupBy { it.sound }.mapValues { (_, v) -> v.sortedByDescending { it.peakConfidence } }

    val timeInBedMs: Long get() = endEpochMs - startEpochMs

    val snoringPercent: Float get() = percentOf(snoringMs)

    fun bandPercent(band: SnoreBand): Float = percentOf(bandMs[band] ?: 0L)

    private fun percentOf(ms: Long): Float = if (monitoredMs <= 0L) 0f else ms * 100f / monitoredMs
}
