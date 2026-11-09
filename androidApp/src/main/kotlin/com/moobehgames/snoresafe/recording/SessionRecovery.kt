package com.moobehgames.snoresafe.recording

import android.util.Log
import com.moobehgames.snoresafe.core.session.EndReason
import com.moobehgames.snoresafe.core.session.SessionAccumulator
import com.moobehgames.snoresafe.data.CheckpointStore

/** Turns a checkpoint left behind by a killed session into a saved (flagged) night. */
class SessionRecovery(
    private val checkpoints: CheckpointStore,
    private val finisher: NightFinisher,
) {
    fun recoverOrphanedSession(): String? {
        val state = checkpoints.read() ?: return null
        return try {
            val summary = SessionAccumulator(state).finish(state.lastFrameEndMs, EndReason.RECOVERED)
            if (summary.monitoredMs > 0) finisher.finish(summary)
            summary.id
        } catch (e: Exception) {
            Log.w("SessionRecovery", "Could not recover session", e)
            null
        } finally {
            checkpoints.clear()
        }
    }
}
