package com.moobehgames.snoresafe.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.moobehgames.snoresafe.appGraph
import com.moobehgames.snoresafe.core.audio.SessionAudio
import com.moobehgames.snoresafe.core.session.SessionSummary
import com.moobehgames.snoresafe.core.settings.AppSettings
import com.moobehgames.snoresafe.core.support.SupportMoment
import com.moobehgames.snoresafe.core.support.SupportPolicy
import com.moobehgames.snoresafe.core.tags.CustomTag
import com.moobehgames.snoresafe.core.tags.NightLog
import com.moobehgames.snoresafe.recording.RecordingState
import com.moobehgames.snoresafe.recording.RecordingStateHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val graph = app.appGraph
    val player = ClipPlayer()

    val sessions: StateFlow<List<SessionSummary>> = graph.sessions.changes
        .map { graph.sessions.all() }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val settings: StateFlow<AppSettings> = graph.settings.settings

    val audioBytes: StateFlow<Long> = graph.audio.changes
        .map { graph.audio.totalBytes() }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    val allAudio: StateFlow<List<SessionAudio>> = graph.audio.changes
        .map { graph.audio.all() }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val logs: StateFlow<Map<String, NightLog>> = graph.logs.changes
        .map { graph.logs.all() }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun saveLog(log: NightLog) = io { graph.logs.save(log) }
    fun setTonightTags(tags: Set<String>) = updateSettings { it.copy(tonightTags = tags) }
    fun addCustomTag(tag: CustomTag) = updateSettings { it.copy(customTags = it.customTags + tag) }
    fun deleteCustomTag(id: String) = updateSettings { s ->
        s.copy(customTags = s.customTags.filterNot { it.id == id }, tonightTags = s.tonightTags - id)
    }

    fun audioFor(sessionId: String): Flow<SessionAudio> =
        graph.audio.changes.map { graph.audio.read(sessionId) }.flowOn(Dispatchers.IO)

    fun clipFile(sessionId: String, fileName: String): File = File(graph.audio.dirFor(sessionId), fileName)

    private fun io(block: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) { block() }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) = io {
        val before = graph.settings.current.storage
        graph.settings.update(transform)
        if (graph.settings.current.storage != before) graph.finisher.applyStorageLimits()
    }

    fun cleanUpNow() = io { graph.finisher.applyStorageLimits() }
    fun deleteNight(id: String) = io { graph.deleteNight(id) }
    fun deleteNightAudio(id: String) = io { graph.audio.deleteSession(id) }
    fun setPinned(sessionId: String, clipId: String, pinned: Boolean) = io { graph.audio.setPinned(sessionId, clipId, pinned) }
    fun deleteClip(sessionId: String, clipId: String) = io { graph.audio.deleteClips(sessionId, setOf(clipId)) }
    fun setProtected(sessionId: String, protected: Boolean) = io { graph.audio.setProtected(sessionId, protected) }

    // --- Support card -------------------------------------------------------------------------

    private val _supportCard = MutableStateFlow(false)
    val supportCard: StateFlow<Boolean> = _supportCard.asStateFlow()

    /** Called at the few good moments; shows the card only if the polite rules allow it. */
    fun offerSupport(moment: SupportMoment?) {
        val s = graph.settings.current
        val recording = RecordingStateHolder.state.value.let { it is RecordingState.Active || it is RecordingState.Waiting || it is RecordingState.Starting }
        if (SupportPolicy.shouldShowCard(s.support, moment, System.currentTimeMillis(), s.onboardingDone, recording)) {
            _supportCard.value = true
            io { graph.settings.update { it.copy(support = SupportPolicy.shown(it.support, System.currentTimeMillis())) } }
        }
    }

    fun dismissSupportCard() {
        _supportCard.value = false
    }

    fun markSupported() {
        _supportCard.value = false
        io { graph.settings.update { it.copy(support = SupportPolicy.supported(it.support)) } }
    }

    override fun onCleared() {
        player.stop()
    }
}
