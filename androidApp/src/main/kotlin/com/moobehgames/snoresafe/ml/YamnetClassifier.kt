package com.moobehgames.snoresafe.ml

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.io.Closeable
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * On-device sound classifier: YAMNet (Apache-2.0) running on LiteRT.
 * Input: ~0.975 s of 16 kHz mono audio. Output: one score per AudioSet class.
 * Runs entirely on the phone; the model ships inside the APK.
 */
class YamnetClassifier private constructor(
    private val interpreter: Interpreter,
    val labels: List<String>,
) : Closeable {

    val sampleRate: Int = 16_000
    val windowSize: Int = interpreter.getInputTensor(0).numElements()

    private val outputElements = interpreter.getOutputTensor(0).numElements()
    private val input: ByteBuffer = ByteBuffer.allocateDirect(windowSize * 4).order(ByteOrder.nativeOrder())
    private val output: ByteBuffer = ByteBuffer.allocateDirect(outputElements * 4).order(ByteOrder.nativeOrder())

    init {
        require(outputElements == labels.size) { "Model has $outputElements outputs but ${labels.size} labels" }
    }

    /** Returns class scores (0..1), aligned with [labels]. */
    fun classify(window: FloatArray): FloatArray {
        require(window.size == windowSize) { "Expected $windowSize samples, got ${window.size}" }
        input.rewind()
        input.asFloatBuffer().put(window)
        output.rewind()
        interpreter.run(input, output)
        output.rewind()
        return FloatArray(outputElements).also { output.asFloatBuffer().get(it) }
    }

    override fun close() = interpreter.close()

    companion object {
        private const val MODEL = "yamnet.tflite"
        private const val LABELS = "yamnet_labels.txt"

        fun create(context: Context): YamnetClassifier {
            val model = context.assets.openFd(MODEL).use { fd ->
                FileInputStream(fd.fileDescriptor).channel.use { channel ->
                    channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
                }
            }
            val labels = context.assets.open(LABELS).bufferedReader().useLines { lines ->
                lines.map { it.trim() }.filter { it.isNotEmpty() }.toList()
            }
            val interpreter = Interpreter(model, Interpreter.Options().setNumThreads(2))
            return YamnetClassifier(interpreter, labels)
        }
    }
}
