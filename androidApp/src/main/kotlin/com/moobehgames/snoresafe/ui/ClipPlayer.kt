package com.moobehgames.snoresafe.ui

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
                setDataSource(file.absolutePath)
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
