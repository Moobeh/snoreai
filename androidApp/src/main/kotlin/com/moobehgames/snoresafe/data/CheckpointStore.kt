package com.moobehgames.snoresafe.data

import android.util.Log
import com.moobehgames.snoresafe.core.session.AccumulatorState
import com.moobehgames.snoresafe.core.session.SessionCodec
import java.io.File

/** Snapshot of the session in progress, written every minute, so a killed app loses at most a minute. */
class CheckpointStore(private val file: File) {

    @Synchronized
    fun write(state: AccumulatorState) = file.writeTextAtomically(SessionCodec.encodeCheckpoint(state))

    @Synchronized
    fun read(): AccumulatorState? {
        if (!file.isFile) return null
        return runCatching { SessionCodec.decodeCheckpoint(file.readText()) }
            .onFailure { Log.w("CheckpointStore", "Discarding unreadable checkpoint", it) }
            .getOrNull()
    }

    @Synchronized
    fun clear() {
        file.delete()
    }
}
