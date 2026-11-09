package com.moobehgames.snoresafe.audio

import android.media.MediaCodec
import android.media.MediaDataSource
import android.media.MediaExtractor
import android.media.MediaFormat
import com.moobehgames.snoresafe.core.insights.ReelPart
import com.moobehgames.snoresafe.data.readSecureBytes
import java.io.File
import java.io.IOException
import java.nio.ByteOrder

/** Stitches highlight slices into one short AAC file for sharing. Decoding happens in memory. */
object ReelBuilder {
    private const val SAMPLE_RATE = 16_000

    fun build(parts: List<ReelPart>, fileFor: (ReelPart) -> File, out: File): File {
        if (parts.isEmpty()) throw IOException("Nothing to put in the reel")
        out.parentFile?.mkdirs()
        val gap = FloatArray((SAMPLE_RATE * com.moobehgames.snoresafe.core.insights.HighlightReel.GAP_MS / 1000).toInt())
        val writer = AacFileWriter(out, SAMPLE_RATE)
        try {
            parts.forEachIndexed { i, part ->
                val pcm = decode(fileFor(part).readSecureBytes())
                val from = (part.fromMs * SAMPLE_RATE / 1000).toInt().coerceIn(0, pcm.size)
                val to = ((part.fromMs + part.durationMs) * SAMPLE_RATE / 1000).toInt().coerceIn(from, pcm.size)
                val slice = pcm.copyOfRange(from, to)
                fade(slice)
                writer.write(slice)
                if (i < parts.lastIndex) writer.write(gap)
            }
        } finally {
            writer.finish()
        }
        return out
    }

    /** Short fades so slices don't click where they join. */
    private fun fade(s: FloatArray) {
        val n = minOf(s.size / 2, SAMPLE_RATE / 50)
        for (i in 0 until n) {
            val g = i / n.toFloat()
            s[i] *= g
            s[s.size - 1 - i] *= g
        }
    }

    /** Decodes a mono AAC recording to floats in [-1, 1]. */
    private fun decode(bytes: ByteArray): FloatArray {
        val extractor = MediaExtractor()
        extractor.setDataSource(object : MediaDataSource() {
            override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
                if (position >= bytes.size) return -1
                val n = minOf(size.toLong(), bytes.size - position).toInt()
                System.arraycopy(bytes, position.toInt(), buffer, offset, n)
                return n
            }
            override fun getSize(): Long = bytes.size.toLong()
            override fun close() = Unit
        })
        val format: MediaFormat = (0 until extractor.trackCount).map(extractor::getTrackFormat)
            .firstOrNull { it.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true } ?: throw IOException("No audio track")
        extractor.selectTrack((0 until extractor.trackCount).first { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true })
        val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
        codec.configure(format, null, null, 0)
        codec.start()
        val out = ArrayList<Float>(SAMPLE_RATE * 15)
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        try {
            while (!outputDone) {
                if (!inputDone) {
                    val i = codec.dequeueInputBuffer(10_000)
                    if (i >= 0) {
                        val buf = codec.getInputBuffer(i)!!
                        val n = extractor.readSampleData(buf, 0)
                        if (n < 0) {
                            codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(i, 0, n, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val o = codec.dequeueOutputBuffer(info, 10_000)
                if (o >= 0) {
                    val buf = codec.getOutputBuffer(o)!!.order(ByteOrder.LITTLE_ENDIAN)
                    buf.position(info.offset).limit(info.offset + info.size)
                    val shorts = buf.asShortBuffer()
                    while (shorts.hasRemaining()) out.add(shorts.get() / 32768f)
                    codec.releaseOutputBuffer(o, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                }
            }
        } finally {
            runCatching { codec.stop() }
            codec.release()
            extractor.release()
        }
        return out.toFloatArray()
    }
}
