package com.moobehgames.snoresafe.data

import java.io.File

/** Writes via a temp file + rename, so a crash mid-write never leaves a corrupt file. Encrypted at rest. */
internal fun File.writeTextAtomically(text: String) = writeBytesSecurely(text.toByteArray(Charsets.UTF_8))

/** Encrypts [bytes] and writes them atomically. */
internal fun File.writeBytesSecurely(bytes: ByteArray) {
    parentFile?.mkdirs()
    val tmp = File(parentFile, "$name.tmp")
    tmp.writeBytes(Secure.encrypt(bytes))
    if (!tmp.renameTo(this)) {
        delete()
        check(tmp.renameTo(this)) { "Could not replace $this" }
    }
}

/** Reads a file written by [writeBytesSecurely]; files from before encryption are read as they are. */
internal fun File.readSecureBytes(): ByteArray = Secure.decrypt(readBytes())

internal fun File.readSecureText(): String = String(readSecureBytes(), Charsets.UTF_8)

/** Encrypts a file written before encryption existed. No-op if already encrypted. */
internal fun File.encryptInPlace(): Boolean {
    val bytes = readBytes()
    if (Secure.isEncrypted(bytes)) return false
    writeBytesSecurely(bytes)
    return true
}
