package com.moobehgames.snoresafe.data

import android.util.Log
import com.moobehgames.snoresafe.core.audio.AudioClip
import com.moobehgames.snoresafe.core.audio.SessionAudio
import com.moobehgames.snoresafe.core.session.SessionCodec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Audio lives in app-private storage: `audio/<sessionId>/` with one `index.json` and the clip files.
 * The index is rewritten after every clip, so a night cut short keeps every finished clip.
 */
class AudioStore(private val root: File) {

    private val _changes = MutableStateFlow(0)
    val changes: StateFlow<Int> = _changes.asStateFlow()

    fun dirFor(sessionId: String): File {
        require(SAFE_ID.matches(sessionId)) { "Bad session id" }
        return File(root, sessionId).apply { mkdirs() }
    }

    fun fileFor(sessionId: String, clip: AudioClip): File = File(dirFor(sessionId), clip.fileName)

    @Synchronized
    fun read(sessionId: String): SessionAudio {
        if (!SAFE_ID.matches(sessionId)) return SessionAudio(sessionId)
        val index = File(File(root, sessionId), INDEX)
        if (!index.isFile) return SessionAudio(sessionId)
        return runCatching { SessionCodec.decodeAudio(index.readText()) }
            .onFailure { Log.w(TAG, "Unreadable audio index for $sessionId", it) }
            .getOrDefault(SessionAudio(sessionId))
    }

    @Synchronized
    fun write(audio: SessionAudio) {
        File(dirFor(audio.sessionId), INDEX).writeTextAtomically(SessionCodec.encodeAudio(audio))
        _changes.value++
    }

    @Synchronized
    fun addClip(sessionId: String, clip: AudioClip) = write(read(sessionId).let { it.copy(clips = it.clips + clip) })

    @Synchronized
    fun setPinned(sessionId: String, clipId: String, pinned: Boolean) =
        write(read(sessionId).let { a -> a.copy(clips = a.clips.map { if (it.id == clipId) it.copy(pinned = pinned) else it }) })

    @Synchronized
    fun setProtected(sessionId: String, protected: Boolean) = write(read(sessionId).copy(protected = protected))

    @Synchronized
    fun deleteClips(sessionId: String, clipIds: Set<String>) {
        if (clipIds.isEmpty()) return
        val audio = read(sessionId)
        audio.clips.filter { it.id in clipIds }.forEach { File(dirFor(sessionId), it.fileName).delete() }
        write(audio.copy(clips = audio.clips.filterNot { it.id in clipIds }))
    }

    @Synchronized
    fun deleteSession(sessionId: String) {
        if (!SAFE_ID.matches(sessionId)) return
        File(root, sessionId).deleteRecursively()
        _changes.value++
    }

    @Synchronized
    fun all(): List<SessionAudio> = (root.listFiles { f -> f.isDirectory } ?: emptyArray()).map { read(it.name) }

    fun totalBytes(): Long = all().sumOf { it.sizeBytes }

    private companion object {
        const val TAG = "AudioStore"
        const val INDEX = "index.json"
        val SAFE_ID = Regex("[A-Za-z0-9-]{1,64}")
    }
}
