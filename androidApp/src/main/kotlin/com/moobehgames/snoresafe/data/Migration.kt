package com.moobehgames.snoresafe.data

import android.util.Log
import java.io.File
import kotlin.concurrent.thread

/**
 * Housekeeping on every start: encrypts anything written before encryption existed (or while the
 * keystore was unavailable) and removes working files left by a crash. Cheap when there's nothing
 * to do: only the first bytes of each file are read to check its header.
 */
object Migration {
    /** Small data files, done before anything reads them; recordings are done in the background. */
    fun run(filesDir: File) {
        val files = filesDir.walkTopDown().filter { it.isFile }.toList()
        files.filter { it.name.endsWith(".part") || it.name.endsWith(".tmp") }.forEach { it.delete() }
        files.filter { it.name.endsWith(".json") }.forEach(::encryptIfNeeded)
        val audio = files.filter { it.name.endsWith(".m4a") && !it.hasSecureHeader() }
        if (audio.isNotEmpty()) {
            thread(name = "snoresafe-migration", isDaemon = true, priority = Thread.MIN_PRIORITY) {
                audio.forEach(::encryptIfNeeded)
                Log.i("Migration", "Encrypted ${audio.size} recordings")
            }
        }
    }

    private fun encryptIfNeeded(f: File) {
        try {
            if (f.isFile && !f.hasSecureHeader()) f.encryptInPlace()
        } catch (e: Exception) {
            Log.w("Migration", "Could not encrypt ${f.name}", e)
        }
    }

    private fun File.hasSecureHeader(): Boolean {
        val head = ByteArray(16)
        val n = inputStream().use { it.read(head) }
        return n == head.size && Secure.isEncrypted(head)
    }
}
