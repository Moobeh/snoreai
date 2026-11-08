package com.moobehgames.snoresafe.recording

import com.moobehgames.snoresafe.core.audio.ClipSelection
import com.moobehgames.snoresafe.core.audio.StorageCleanup
import com.moobehgames.snoresafe.core.session.SessionSummary
import com.moobehgames.snoresafe.core.support.SupportMoment
import com.moobehgames.snoresafe.core.support.SupportPolicy
import com.moobehgames.snoresafe.data.AudioStore
import com.moobehgames.snoresafe.data.SessionFileStore
import com.moobehgames.snoresafe.data.SettingsStore

/** Everything that happens when a night ends, whether stopped by the user or recovered. */
class NightFinisher(
    private val sessions: SessionFileStore,
    private val audio: AudioStore,
    private val settings: SettingsStore,
) {
    /** Saves the night, trims its clips for the recording mode, applies storage limits. */
    fun finish(summary: SessionSummary): SupportMoment? {
        val history = sessions.all().filter { it.id != summary.id }
        sessions.save(summary)
        val nightAudio = audio.read(summary.id)
        audio.deleteClips(summary.id, ClipSelection.toDelete(nightAudio.clips, summary.recordingMode))
        applyStorageLimits()
        return SupportPolicy.momentAfterNight(summary, history)
    }

    /** Also run when the user changes storage settings. */
    fun applyStorageLimits() {
        val order = sessions.all().map { it.id } // newest first
        val byId = audio.all().associateBy { it.sessionId }
        // Audio folders without a saved night (e.g. a deleted night) are orphans: remove them.
        byId.keys.filterNot { it in order }.forEach { audio.deleteSession(it) }
        val nights = order.mapNotNull { byId[it] }
        StorageCleanup.clipsToDelete(nights, settings.current.storage).forEach { (id, clips) -> audio.deleteClips(id, clips) }
    }
}
