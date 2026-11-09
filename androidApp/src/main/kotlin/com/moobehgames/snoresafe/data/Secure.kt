package com.moobehgames.snoresafe.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import java.io.IOException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Where the data key comes from. Production: Android Keystore. Tests: a software key. */
fun interface KeySource {
    /** Null only where no keystore exists (e.g. JVM render tests); then data is stored unencrypted. */
    fun key(): SecretKey?
}

/**
 * Encryption at rest for everything SnoreSafe stores: nights, notes, settings, checkpoints,
 * the personal model and recordings.
 *
 * Format: "SSE1" | 12-byte IV | AES-256-GCM ciphertext + tag. The key is generated inside the
 * Android Keystore and can't be read out of the phone. Uninstalling deletes key and data together.
 * Files from before encryption (no "SSE1" header) are still readable and get encrypted on migration.
 */
object Secure {
    private val MAGIC = byteArrayOf('S'.code.toByte(), 'S'.code.toByte(), 'E'.code.toByte(), '1'.code.toByte())
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128

    @Volatile
    var keys: KeySource = KeystoreKeys

    fun isEncrypted(bytes: ByteArray): Boolean =
        bytes.size >= MAGIC.size + IV_BYTES && (MAGIC.indices).all { bytes[it] == MAGIC[it] }

    fun encrypt(plain: ByteArray): ByteArray {
        val key = keys.key() ?: return plain
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        check(iv.size == IV_BYTES) { "Unexpected IV size" }
        return MAGIC + iv + cipher.doFinal(plain)
    }

    fun decrypt(data: ByteArray): ByteArray {
        if (!isEncrypted(data)) return data // written before encryption
        val key = keys.key() ?: throw IOException("Encrypted data but no key available")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, data, MAGIC.size, IV_BYTES))
        return cipher.doFinal(data, MAGIC.size + IV_BYTES, data.size - MAGIC.size - IV_BYTES)
    }
}

/** AES-256 key generated and held by the Android Keystore. */
object KeystoreKeys : KeySource {
    private const val ALIAS = "snoresafe-data-v1"
    private const val PROVIDER = "AndroidKeyStore"

    @Volatile
    private var cached: SecretKey? = null

    @Volatile
    private var unavailable = false

    @Synchronized
    override fun key(): SecretKey? {
        cached?.let { return it }
        if (unavailable) return null
        return try {
            val store = KeyStore.getInstance(PROVIDER).apply { load(null) }
            val existing = (store.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
            (existing ?: generate()).also { cached = it }
        } catch (e: java.security.KeyStoreException) {
            // No Android Keystore at all (only happens off-device, e.g. JVM tests).
            Log.w("Secure", "Keystore unavailable; storing unencrypted", e)
            unavailable = true
            null
        } catch (e: Exception) {
            // A faulty keystore on a device: keep the app usable rather than lose tonight's data.
            // Files written meanwhile are unencrypted and get encrypted by the next migration pass.
            Log.e("Secure", "Keystore error; this write is unencrypted", e)
            null
        }
    }

    private fun generate(): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }
}
