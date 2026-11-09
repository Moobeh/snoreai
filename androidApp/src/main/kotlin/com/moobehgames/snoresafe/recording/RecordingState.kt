package com.moobehgames.snoresafe.recording

import com.moobehgames.snoresafe.core.analysis.NightSound
import com.moobehgames.snoresafe.core.analysis.SnoreBand
import com.moobehgames.snoresafe.core.support.SupportMoment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface RecordingState {
    data object Idle : RecordingState
    data object Starting : RecordingState

    /** Start delay: listening begins at [listenAtEpochMs]. */
    data class Waiting(val listenAtEpochMs: Long) : RecordingState

    data class Active(
        val startEpochMs: Long,
        val elapsedMs: Long,
        val paused: Boolean,
        val snoringNow: Boolean,
        val band: SnoreBand,
        val intensity: Float,
        val scoreSoFar: Int,
        val lastSound: NightSound? = null,
        /** When a pause ends by itself; null if not paused or auto-resume is off. */
        val autoResumeAtEpochMs: Long? = null,
    ) : RecordingState

    /** Session ended; [sessionId] is the night just saved. */
    data class Finished(val sessionId: String, val supportMoment: SupportMoment? = null) : RecordingState

    data class Failed(val reason: Reason, val detail: String? = null) : RecordingState

    enum class Reason { MICROPHONE, MODEL, PERMISSION, OTHER }
}

/** Process-wide live state, published by [RecordingService] and observed by the UI. */
object RecordingStateHolder {
    private val _state = MutableStateFlow<RecordingState>(RecordingState.Idle)
    val state: StateFlow<RecordingState> = _state.asStateFlow()

    internal fun set(value: RecordingState) {
        _state.value = value
    }

    fun acknowledge() {
        val s = _state.value
        if (s is RecordingState.Finished || s is RecordingState.Failed) _state.value = RecordingState.Idle
    }
}
