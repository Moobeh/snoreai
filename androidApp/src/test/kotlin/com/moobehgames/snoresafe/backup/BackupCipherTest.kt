package com.moobehgames.snoresafe.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BackupCipherTest {
    private val password = "correct horse battery".toCharArray()

    // Low iteration count keeps tests fast; production uses DEFAULT_ITERATIONS.
    private fun encrypt(data: ByteArray, pw: CharArray = password): ByteArray {
        val out = ByteArrayOutputStream()
        BackupCipher.encryptingStream(out, pw, iterations = 10_000, random = SecureRandom()).use { it.write(data) }
        return out.toByteArray()
    }

    private fun decrypt(blob: ByteArray, pw: CharArray = password): ByteArray =
        BackupCipher.decryptingStream(ByteArrayInputStream(blob), pw).use { it.readBytes() }

    @Test fun roundTripAcrossManyChunks() {
        val data = Random(3).nextBytes(64 * 1024 * 3 + 1234)
        assertContentEquals(data, decrypt(encrypt(data)))
    }

    @Test fun roundTripEmptyAndExactChunk() {
        assertContentEquals(ByteArray(0), decrypt(encrypt(ByteArray(0))))
        val exact = Random(4).nextBytes(64 * 1024)
        assertContentEquals(exact, decrypt(encrypt(exact)))
    }

    @Test fun wrongPasswordIsRejected() {
        val blob = encrypt("nights".toByteArray())
        assertFailsWith<BackupCipher.WrongPasswordOrCorrupt> { decrypt(blob, "wrong password".toCharArray()) }
    }

    @Test fun truncatedBackupIsRejected() {
        val blob = encrypt(Random(5).nextBytes(200_000))
        // Cut off the final chunk: earlier chunks still verify, but the missing "last" chunk must be noticed.
        val firstChunkEnd = 6 + 1 + 16 + 4 + 8 + 4 + 64 * 1024 + 16
        assertFailsWith<BackupCipher.WrongPasswordOrCorrupt> { decrypt(blob.copyOf(firstChunkEnd)) }
        assertFailsWith<BackupCipher.WrongPasswordOrCorrupt> { decrypt(blob.copyOf(blob.size - 1)) }
    }

    @Test fun tamperingIsRejected() {
        val blob = encrypt(Random(6).nextBytes(1000))
        blob[blob.size - 20] = (blob[blob.size - 20].toInt() xor 1).toByte()
        assertFailsWith<BackupCipher.WrongPasswordOrCorrupt> { decrypt(blob) }
    }

    @Test fun notABackupIsRejected() {
        assertFailsWith<BackupCipher.WrongPasswordOrCorrupt> { decrypt("hello world, not a backup".toByteArray()) }
    }

    @Test fun pbkdf2MatchesPublishedTestVectors() {
        fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
        assertEquals(
            "120fb6cffcf8b32c43e7225256c4f837a86548c92ccc35480805987cb70be17b",
            hex(BackupCipher.pbkdf2("password".toCharArray(), "salt".toByteArray(), 1, 32)),
        )
        assertEquals(
            "c5e478d59288c841aa530db6845c4c8d962893a001ce4e11a4963873aa98134a",
            hex(BackupCipher.pbkdf2("password".toCharArray(), "salt".toByteArray(), 4096, 32)),
        )
    }
}
