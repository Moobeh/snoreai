package com.moobehgames.snoresafe.audio

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.Closeable
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Encodes mono float PCM to an AAC `.m4a` file with the phone's built-in encoder.
 * About 15 MB per hour at 32 kbit/s, versus ~115 MB for raw audio.
 */
class AacFileWriter(private val file: File, private val sampleRate: Int, bitRate: Int = 32_000) : Closeable {

    private val codec: MediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
    private val muxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    private val info = MediaCodec.BufferInfo()
    private var track = -1
    private var muxerStarted = false
    private var samplesQueued = 0L
    private var samplesWritten = 0L
    private var closed = false

    init {
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16_384)
        }
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
    }

    val durationMs: Long get() = samplesQueued * 1000 / sampleRate

    fun write(samples: FloatArray, offset: Int = 0, length: Int = samples.size - offset) {
        check(!closed) { "Writer is closed" }
        var pos = offset
        val end = offset + length
        while (pos < end) {
            val index = codec.dequeueInputBuffer(TIMEOUT_US)
            if (index >= 0) {
                val buffer = codec.getInputBuffer(index)!!.order(ByteOrder.LITTLE_ENDIAN)
                buffer.clear()
                val count = minOf(end - pos, buffer.remaining() / 2)
                for (i in 0 until count) {
                    buffer.putShort((samples[pos + i].coerceIn(-1f, 1f) * 32767f).toInt().toShort())
                }
                codec.queueInputBuffer(index, 0, count * 2, presentationUs(samplesQueued), 0)
                samplesQueued += count
                pos += count
            }
            drain(endOfStream = false)
        }
    }

    /** Finishes the file. Returns false (and deletes it) if no audio was written. */
    fun finish(): Boolean {
        if (closed) return samplesWritten > 0
        closed = true
        try {
            var index = codec.dequeueInputBuffer(TIMEOUT_US * 10)
            var tries = 0
            while (index < 0 && tries++ < 50) {
                drain(endOfStream = false)
                index = codec.dequeueInputBuffer(TIMEOUT_US * 10)
            }
            if (index >= 0) codec.queueInputBuffer(index, 0, 0, presentationUs(samplesQueued), MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            drain(endOfStream = true)
        } finally {
            runCatching { codec.stop() }
            codec.release()
            if (muxerStarted) runCatching { muxer.stop() }
            runCatching { muxer.release() }
        }
        if (samplesWritten == 0L) file.delete()
        return samplesWritten > 0
    }

    override fun close() {
        finish()
    }

    private fun drain(endOfStream: Boolean) {
        var idleTries = 0
        while (true) {
            val index = codec.dequeueOutputBuffer(info, TIMEOUT_US)
            when {
                index == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!endOfStream || idleTries++ > 100) return
                }
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    track = muxer.addTrack(codec.outputFormat)
                    muxer.start()
                    muxerStarted = true
                }
                index >= 0 -> {
                    val out: ByteBuffer = codec.getOutputBuffer(index)!!
                    val isConfig = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                    if (!isConfig && info.size > 0 && muxerStarted) {
                        out.position(info.offset).limit(info.offset + info.size)
                        muxer.writeSampleData(track, out, info)
                        samplesWritten++
                    }
                    codec.releaseOutputBuffer(index, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                }
            }
        }
    }

    private fun presentationUs(samples: Long) = samples * 1_000_000 / sampleRate

    private companion object {
        const val TIMEOUT_US = 10_000L
    }
}
