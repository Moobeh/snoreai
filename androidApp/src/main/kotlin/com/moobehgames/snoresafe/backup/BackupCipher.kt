package com.moobehgames.snoresafe.backup

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Password-encrypted backup stream. Streams in 64 KiB chunks, so backups of any size never need
 * to fit in memory.
 *
 * File: "SNSFBK" | version(1) | salt(16) | iterations(4) | noncePrefix(8) | chunks…
 * Chunk: length(4) | AES-256-GCM(ciphertext + 16-byte tag)
 * Each chunk's nonce = noncePrefix || counter. Its associated data binds the header, the counter
 * and whether it is the last chunk, so chunks can't be reordered, dropped or truncated unnoticed.
 * Key = PBKDF2-HMAC-SHA256(password, salt, iterations).
 */
object BackupCipher {
    private val MAGIC = "SNSFBK".toByteArray(Charsets.US_ASCII)
    private const val VERSION: Byte = 1
    const val DEFAULT_ITERATIONS = 310_000
    private const val CHUNK = 64 * 1024
    private const val TAG_BITS = 128

    class WrongPasswordOrCorrupt : IOException("Wrong password or damaged backup")

    fun encryptingStream(out: OutputStream, password: CharArray, iterations: Int = DEFAULT_ITERATIONS, random: SecureRandom = SecureRandom()): OutputStream {
        val salt = ByteArray(16).also(random::nextBytes)
        val prefix = ByteArray(8).also(random::nextBytes)
        val header = ByteArrayOutputStream().apply {
            write(MAGIC)
            write(VERSION.toInt())
            write(salt)
            DataOutputStream(this).writeInt(iterations)
            write(prefix)
        }.toByteArray()
        out.write(header)
        return ChunkWriter(out, deriveKey(password, salt, iterations), prefix, header)
    }

    fun decryptingStream(input: InputStream, password: CharArray): InputStream {
        val data = DataInputStream(input)
        val magic = ByteArray(MAGIC.size).also { data.readFullyOr(it) }
        if (!magic.contentEquals(MAGIC)) throw WrongPasswordOrCorrupt()
        val version = data.read()
        if (version != VERSION.toInt()) throw IOException("Unsupported backup version $version")
        val salt = ByteArray(16).also { data.readFullyOr(it) }
        val iterations = data.readInt()
        if (iterations !in 10_000..10_000_000) throw WrongPasswordOrCorrupt()
        val prefix = ByteArray(8).also { data.readFullyOr(it) }
        val header = ByteArrayOutputStream().apply {
            write(MAGIC); write(version); write(salt); DataOutputStream(this).writeInt(iterations); write(prefix)
        }.toByteArray()
        return ChunkReader(data, deriveKey(password, salt, iterations), prefix, header)
    }

    private fun DataInputStream.readFullyOr(b: ByteArray) = try {
        readFully(b)
    } catch (e: EOFException) {
        throw WrongPasswordOrCorrupt()
    }

    private fun nonce(prefix: ByteArray, counter: Int) = ByteBuffer.allocate(12).put(prefix).putInt(counter).array()

    private fun aad(header: ByteArray, counter: Int, last: Boolean) =
        ByteBuffer.allocate(header.size + 5).put(header).putInt(counter).put(if (last) 1 else 0).array()

    private class ChunkWriter(
        private val out: OutputStream,
        private val key: SecretKeySpec,
        private val prefix: ByteArray,
        private val header: ByteArray,
    ) : OutputStream() {
        private val buffer = ByteArray(CHUNK)
        private var filled = 0
        private var counter = 0
        private var closed = false

        override fun write(b: Int) {
            if (filled == CHUNK) flushChunk(last = false)
            buffer[filled++] = b.toByte()
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            var pos = off
            var remaining = len
            while (remaining > 0) {
                if (filled == CHUNK) flushChunk(last = false)
                val n = minOf(remaining, CHUNK - filled)
                System.arraycopy(b, pos, buffer, filled, n)
                filled += n
                pos += n
                remaining -= n
            }
        }

        private fun flushChunk(last: Boolean) {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce(prefix, counter)))
            cipher.updateAAD(aad(header, counter, last))
            val sealed = cipher.doFinal(buffer, 0, filled)
            DataOutputStream(out).writeInt(sealed.size)
            out.write(sealed)
            counter++
            filled = 0
        }

        override fun flush() = out.flush()

        override fun close() {
            if (closed) return
            closed = true
            flushChunk(last = true) // always ends with a "last" chunk, even if empty
            out.flush()
            out.close()
        }
    }

    private class ChunkReader(
        private val input: DataInputStream,
        private val key: SecretKeySpec,
        private val prefix: ByteArray,
        private val header: ByteArray,
    ) : InputStream() {
        private var plain = ByteArray(0)
        private var pos = 0
        private var counter = 0
        private var finished = false

        private fun next(): Boolean {
            if (finished) return false
            val len = try {
                input.readInt()
            } catch (e: EOFException) {
                throw WrongPasswordOrCorrupt() // stream ended without its last chunk
            }
            if (len < 16 || len > CHUNK + 16) throw WrongPasswordOrCorrupt()
            val sealed = ByteArray(len)
            try {
                input.readFully(sealed)
            } catch (e: EOFException) {
                throw WrongPasswordOrCorrupt()
            }
            // We don't know if this is the last chunk until it verifies one way or the other.
            plain = open(sealed, last = false) ?: open(sealed, last = true)?.also { finished = true } ?: throw WrongPasswordOrCorrupt()
            pos = 0
            counter++
            if (finished && input.read() != -1) throw WrongPasswordOrCorrupt() // data after the last chunk
            return true
        }

        private fun open(sealed: ByteArray, last: Boolean): ByteArray? = try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce(prefix, counter)))
            cipher.updateAAD(aad(header, counter, last))
            cipher.doFinal(sealed)
        } catch (e: AEADBadTagException) {
            null
        }

        override fun read(): Int {
            while (pos >= plain.size) if (!next()) return -1
            return plain[pos++].toInt() and 0xff
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            while (pos >= plain.size) if (!next()) return -1
            val n = minOf(len, plain.size - pos)
            System.arraycopy(plain, pos, b, off, n)
            pos += n
            return n
        }

        override fun close() = input.close()
    }

    /** PBKDF2-HMAC-SHA256, implemented directly so it works on every Android version we support. */
    fun pbkdf2(password: CharArray, salt: ByteArray, iterations: Int, keyBytes: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        val pw = String(password).toByteArray(Charsets.UTF_8)
        require(pw.isNotEmpty()) { "Password must not be empty" }
        mac.init(SecretKeySpec(pw, "HmacSHA256"))
        val out = ByteArray(keyBytes)
        var block = 1
        var offset = 0
        while (offset < keyBytes) {
            mac.update(salt)
            mac.update(ByteBuffer.allocate(4).putInt(block).array())
            var u = mac.doFinal()
            val t = u.copyOf()
            repeat(iterations - 1) {
                u = mac.doFinal(u)
                for (i in t.indices) t[i] = (t[i].toInt() xor u[i].toInt()).toByte()
            }
            val n = minOf(t.size, keyBytes - offset)
            System.arraycopy(t, 0, out, offset, n)
            offset += n
            block++
        }
        pw.fill(0)
        return out
    }

    private fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int) = SecretKeySpec(pbkdf2(password, salt, iterations, 32), "AES")
}
