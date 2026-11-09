package com.moobehgames.snoresafe.data

import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Encryption at rest, with a software key standing in for the Android Keystore. */
class SecureTest {
    @get:Rule val tmp = TemporaryFolder()

    private val key: SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val previous = Secure.keys

    @Before fun useSoftwareKey() {
        Secure.keys = KeySource { key }
    }

    @After fun restore() {
        Secure.keys = previous
    }

    @Test fun writesAreEncryptedAndReadBack() {
        val f = tmp.newFile("night.json")
        f.writeTextAtomically("""{"snoreScore":12}""")
        val raw = f.readBytes()
        assertTrue(Secure.isEncrypted(raw))
        assertFalse(String(raw, Charsets.ISO_8859_1).contains("snoreScore"))
        assertEquals("""{"snoreScore":12}""", f.readSecureText())
    }

    @Test fun eachWriteUsesAFreshIv() {
        assertFalse(Secure.encrypt(ByteArray(32)).contentEquals(Secure.encrypt(ByteArray(32))))
    }

    @Test fun filesFromBeforeEncryptionStillReadAndMigrate() {
        val f = tmp.newFile("old.json").apply { writeText("""{"legacy":true}""") }
        assertEquals("""{"legacy":true}""", f.readSecureText())
        assertTrue(f.encryptInPlace())
        assertTrue(Secure.isEncrypted(f.readBytes()))
        assertFalse(f.encryptInPlace()) // already done
        assertEquals("""{"legacy":true}""", f.readSecureText())
    }

    @Test fun migrationEncryptsDataAndClearsLeftovers() {
        val root = tmp.newFolder("files")
        val json = java.io.File(root, "sessions/a.json").apply { parentFile!!.mkdirs(); writeText("{}") }
        val part = java.io.File(root, "audio/a/x.m4a.part").apply { parentFile!!.mkdirs(); writeBytes(ByteArray(10)) }
        Migration.run(root)
        assertTrue(Secure.isEncrypted(json.readBytes()))
        assertFalse(part.exists())
    }

    @Test fun tamperedDataIsRejected() {
        val sealed = Secure.encrypt("secret".toByteArray())
        sealed[sealed.size - 1] = (sealed.last().toInt() xor 1).toByte()
        assertFailsWith<javax.crypto.AEADBadTagException> { Secure.decrypt(sealed) }
    }

    @Test fun binaryAudioRoundTrips() {
        val audio = ByteArray(300_000) { (it * 31).toByte() }
        assertContentEquals(audio, Secure.decrypt(Secure.encrypt(audio)))
    }
}
