package com.moobehgames.snoresafe.ui

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import android.media.MediaDataSource
import com.moobehgames.snoresafe.data.readSecureBytes
import java.io.File

/** Plays one recording at a time. */
class ClipPlayer {
    data class State(val clipId: String? = null, val playing: Boolean = false)

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var player: MediaPlayer? = null

    val positionMs: Int get() = runCatching { player?.currentPosition ?: 0 }.getOrDefault(0)
    val durationMs: Int get() = runCatching { player?.duration ?: 0 }.getOrDefault(0)

    /** Play [file] (from [startMs]); if it's already the current clip, pause / resume instead. */
    fun toggle(clipId: String, file: File, startMs: Int = 0) {
        val p = player
        val s = _state.value
        if (p != null && s.clipId == clipId && startMs == 0) {
            if (s.playing) p.pause() else p.start()
            _state.value = s.copy(playing = !s.playing)
            return
        }
        stop()
        try {
            player = MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                // Recordings are encrypted on disk; play them from memory, never from a decrypted file.
                setDataSource(ByteArrayDataSource(file.readSecureBytes()))
                setOnCompletionListener { _state.value = _state.value.copy(playing = false) }
                prepare()
                if (startMs > 0) seekTo(startMs.coerceAtMost(duration - 500).coerceAtLeast(0))
                start()
            }
            _state.value = State(clipId, true)
        } catch (e: Exception) {
            Log.w("ClipPlayer", "Can't play $file", e)
            stop()
        }
    }

    fun stop() {
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
        _state.value = State()
    }
}

private class ByteArrayDataSource(private val data: ByteArray) : MediaDataSource() {
    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        if (position >= data.size) return -1
        val n = minOf(size.toLong(), data.size - position).toInt()
        System.arraycopy(data, position.toInt(), buffer, offset, n)
        return n
    }

    override fun getSize(): Long = data.size.toLong()

    override fun close() = Unit
}
