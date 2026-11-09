package com.moobehgames.snoresafe.data

import android.util.Log
import com.moobehgames.snoresafe.core.session.SessionCodec
import com.moobehgames.snoresafe.core.session.SessionSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * One JSON file per night in app-private storage.
 * TODO(v1): encrypt at rest and move to a proper database once clips and labels land.
 */
class SessionFileStore(private val dir: File) {

    private val _changes = MutableStateFlow(0)

    /** Ticks whenever a night is saved or deleted. */
    val changes: StateFlow<Int> = _changes.asStateFlow()

    @Synchronized
    fun save(summary: SessionSummary) {
        require(SAFE_ID.matches(summary.id)) { "Bad session id" }
        File(dir, "${summary.id}.json").writeTextAtomically(SessionCodec.encodeSummary(summary))
        _changes.value++
    }

    @Synchronized
    fun all(): List<SessionSummary> =
        (dir.listFiles { f -> f.isFile && f.name.endsWith(".json") } ?: emptyArray())
            .mapNotNull { f ->
                runCatching { SessionCodec.decodeSummary(f.readSecureText()) }
                    .onFailure { Log.w(TAG, "Skipping unreadable session ${f.name}", it) }
                    .getOrNull()
            }
            .sortedByDescending { it.startEpochMs }

    @Synchronized
    fun get(id: String): SessionSummary? {
        if (!SAFE_ID.matches(id)) return null
        val f = File(dir, "$id.json")
        return if (f.isFile) runCatching { SessionCodec.decodeSummary(f.readSecureText()) }.getOrNull() else null
    }

    @Synchronized
    fun delete(id: String) {
        if (SAFE_ID.matches(id) && File(dir, "$id.json").delete()) _changes.value++
    }

    private companion object {
        const val TAG = "SessionFileStore"
        val SAFE_ID = Regex("[A-Za-z0-9-]{1,64}")
    }
}
