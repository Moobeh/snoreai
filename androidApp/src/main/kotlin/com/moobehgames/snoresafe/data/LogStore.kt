package com.moobehgames.snoresafe.data

import android.util.Log
import com.moobehgames.snoresafe.core.session.SessionCodec
import com.moobehgames.snoresafe.core.tags.NightLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/** The user's own additions to each night (remedies, factors, notes, weight, rest), one file per night. */
class LogStore(private val dir: File) {
    private val _changes = MutableStateFlow(0)
    val changes: StateFlow<Int> = _changes.asStateFlow()

    @Synchronized
    fun get(sessionId: String): NightLog {
        if (!SAFE_ID.matches(sessionId)) return NightLog(sessionId)
        val f = File(dir, "$sessionId.json")
        if (!f.isFile) return NightLog(sessionId)
        return runCatching { SessionCodec.decodeLog(f.readSecureText()) }
            .onFailure { Log.w("LogStore", "Unreadable log for $sessionId", it) }
            .getOrDefault(NightLog(sessionId))
    }

    @Synchronized
    fun save(log: NightLog) {
        require(SAFE_ID.matches(log.sessionId)) { "Bad session id" }
        val f = File(dir, "${log.sessionId}.json")
        if (log.isEmpty) f.delete() else f.writeTextAtomically(SessionCodec.encodeLog(log))
        _changes.value++
    }

    @Synchronized
    fun all(): Map<String, NightLog> =
        (dir.listFiles { f -> f.isFile && f.name.endsWith(".json") } ?: emptyArray())
            .mapNotNull { f -> runCatching { SessionCodec.decodeLog(f.readSecureText()) }.getOrNull() }
            .associateBy { it.sessionId }

    @Synchronized
    fun delete(sessionId: String) {
        if (SAFE_ID.matches(sessionId) && File(dir, "$sessionId.json").delete()) _changes.value++
    }

    private companion object {
        val SAFE_ID = Regex("[A-Za-z0-9-]{1,64}")
    }
}
