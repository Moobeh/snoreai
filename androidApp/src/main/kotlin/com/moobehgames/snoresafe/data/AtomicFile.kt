package com.moobehgames.snoresafe.data

import java.io.File

/** Writes via a temp file + rename, so a crash mid-write never leaves a corrupt file. */
internal fun File.writeTextAtomically(text: String) {
    parentFile?.mkdirs()
    val tmp = File(parentFile, "$name.tmp")
    tmp.writeText(text)
    if (!tmp.renameTo(this)) {
        delete()
        check(tmp.renameTo(this)) { "Could not replace $this" }
    }
}
