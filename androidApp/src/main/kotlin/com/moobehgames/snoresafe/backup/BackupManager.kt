package com.moobehgames.snoresafe.backup

import android.content.Context
import android.net.Uri
import com.moobehgames.snoresafe.AppGraph
import com.moobehgames.snoresafe.core.session.SessionCodec
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Backup = an encrypted zip of every night and its recordings, written to a file the user picks.
 * Restore merges: nights already on the phone are kept, missing ones are added.
 */
class BackupManager(private val context: Context, private val graph: AppGraph) {

    fun create(uri: Uri, password: CharArray) {
        val out = context.contentResolver.openOutputStream(uri, "w") ?: throw IOException("Can't open file")
        BackupCipher.encryptingStream(out, password).use { encrypted ->
            ZipOutputStream(encrypted).use { zip ->
                zip.putText(MANIFEST, """{"format":"snoresafe-backup","version":1,"createdAt":${System.currentTimeMillis()}}""")
                val custom = graph.settings.current.customTags
                if (custom.isNotEmpty()) {
                    zip.putText(CUSTOM_TAGS, SessionCodec.encodeCustomTags(custom))
                }
                val labels = graph.personalModel.exportSet()
                if (labels.isNotEmpty()) zip.putText(TRAINING_SET, SessionCodec.encodeTrainingSet(labels))
                for (s in graph.sessions.all()) {
                    zip.putText("sessions/${s.id}.json", SessionCodec.encodeSummary(s))
                    val log = graph.logs.get(s.id)
                    if (!log.isEmpty) zip.putText("logs/${s.id}.json", SessionCodec.encodeLog(log))
                    val audio = graph.audio.read(s.id)
                    if (audio.clips.isEmpty() && !audio.protected) continue
                    zip.putText("audio/${s.id}/index.json", SessionCodec.encodeAudio(audio))
                    for (clip in audio.clips) {
                        val f = graph.audio.fileFor(s.id, clip)
                        if (!f.isFile) continue
                        zip.putNextEntry(ZipEntry("audio/${s.id}/${clip.fileName}"))
                        f.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
            }
        }
    }

    /** Returns how many nights were added. */
    fun restore(uri: Uri, password: CharArray): Int {
        val staging = File(context.cacheDir, "restore-${UUID.randomUUID()}")
        try {
            val input = context.contentResolver.openInputStream(uri) ?: throw IOException("Can't open file")
            var sawManifest = false
            BackupCipher.decryptingStream(input, password).use { decrypted ->
                ZipInputStream(decrypted).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        if (entry.isDirectory) continue
                        val name = entry.name
                        if (name == MANIFEST) sawManifest = true
                        // Only exact, expected paths: no "..", no absolute paths (zip-slip safe).
                        if (name != MANIFEST && name != CUSTOM_TAGS && name != TRAINING_SET && !SESSION_PATH.matches(name) && !LOG_PATH.matches(name) && !AUDIO_PATH.matches(name)) continue
                        val target = File(staging, name)
                        target.parentFile?.mkdirs()
                        target.outputStream().use { zip.copyTo(it) }
                    }
                }
            }
            if (!sawManifest) throw BackupCipher.WrongPasswordOrCorrupt()

            // Custom remedies and factors: add any the phone doesn't have yet, so restored nights show their names.
            File(staging, CUSTOM_TAGS).takeIf { it.isFile }?.let { f ->
                val restored = runCatching { SessionCodec.decodeCustomTags(f.readText()) }.getOrDefault(emptyList())
                graph.settings.update { s -> s.copy(customTags = s.customTags + restored.filter { r -> s.customTags.none { it.id == r.id } }) }
            }

            val existing = graph.sessions.all().map { it.id }.toSet()
            var added = 0
            File(staging, "sessions").listFiles()?.forEach { f ->
                val summary = runCatching { SessionCodec.decodeSummary(f.readText()) }.getOrNull() ?: return@forEach
                if (summary.id in existing) return@forEach
                val audioDir = File(staging, "audio/${summary.id}")
                val index = File(audioDir, "index.json")
                if (index.isFile) {
                    val audio = runCatching { SessionCodec.decodeAudio(index.readText()) }.getOrNull()
                    if (audio != null && audio.sessionId == summary.id) {
                        val dest = graph.audio.dirFor(summary.id)
                        audio.clips.forEach { clip ->
                            val src = File(audioDir, clip.fileName)
                            if (SAFE_FILE.matches(clip.fileName) && src.isFile) src.copyTo(File(dest, clip.fileName), overwrite = true)
                        }
                        graph.audio.write(audio.copy(clips = audio.clips.filter { File(dest, it.fileName).isFile }))
                    }
                }
                File(staging, "logs/${summary.id}.json").takeIf { it.isFile }?.let { f ->
                    runCatching { SessionCodec.decodeLog(f.readText()) }.getOrNull()
                        ?.takeIf { it.sessionId == summary.id }
                        ?.let { graph.logs.save(it) }
                }
                graph.sessions.save(summary)
                added++
            }
            File(staging, TRAINING_SET).takeIf { it.isFile }?.let { f ->
                runCatching { SessionCodec.decodeTrainingSet(f.readText()) }.getOrNull()?.let { graph.personalModel.importSet(it) }
            }
            graph.finisher.applyStorageLimits()
            return added
        } finally {
            staging.deleteRecursively()
            password.fill('\u0000')
        }
    }

    private fun ZipOutputStream.putText(name: String, text: String) {
        putNextEntry(ZipEntry(name))
        write(text.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private companion object {
        const val MANIFEST = "manifest.json"
        const val CUSTOM_TAGS = "custom-tags.json"
        const val TRAINING_SET = "personal-training-set.json"
        val LOG_PATH = Regex("logs/[A-Za-z0-9-]{1,64}\\.json")
        val SESSION_PATH = Regex("sessions/[A-Za-z0-9-]{1,64}\\.json")
        val AUDIO_PATH = Regex("audio/[A-Za-z0-9-]{1,64}/[A-Za-z0-9._-]{1,80}")
        val SAFE_FILE = Regex("[A-Za-z0-9-][A-Za-z0-9._-]{0,79}")
    }
}
