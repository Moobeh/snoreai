package com.moobehgames.snoresafe.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.Closeable
import java.io.IOException

/**
 * 16-bit mono microphone stream converted to floats in [-1, 1].
 * Uses the VOICE_RECOGNITION source, which skips automatic gain control on most phones,
 * so loudness stays comparable across the night.
 */
class MicSource(private val sampleRate: Int) : Closeable {

    private val record: AudioRecord
    private var buffer = ShortArray(sampleRate)

    init {
        val minBuffer = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuffer <= 0) throw IOException("Sample rate $sampleRate not supported")
        record = createRecord(maxOf(minBuffer, sampleRate * 2) * 2)
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            throw IOException("Microphone could not be initialised")
        }
        record.startRecording()
        if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            record.release()
            throw IOException("Microphone is in use by another app")
        }
    }

    // The caller (RecordingService) checks RECORD_AUDIO before creating a MicSource.
    @SuppressLint("MissingPermission")
    private fun createRecord(bufferBytes: Int) = AudioRecord(
        MediaRecorder.AudioSource.VOICE_RECOGNITION,
        sampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT,
        bufferBytes,
    )

    /** Blocks until [length] samples have been read into [dest] at [offset]. */
    fun readFully(dest: FloatArray, offset: Int, length: Int) {
        if (buffer.size < length) buffer = ShortArray(length)
        var read = 0
        while (read < length) {
            val n = record.read(buffer, read, length - read)
            if (n < 0) throw IOException("Microphone read failed ($n)")
            read += n
        }
        for (i in 0 until length) dest[offset + i] = buffer[i] / 32768f
    }

    override fun close() {
        runCatching { record.stop() }
        record.release()
    }
}
