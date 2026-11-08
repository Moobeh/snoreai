package com.moobehgames.snoresafe.core.audio

import com.moobehgames.snoresafe.core.settings.StorageSettings

/**
 * Decides which clips to delete to respect the user's storage settings. Pure: callers delete the files.
 *
 * 1. Protected nights and pinned clips are never touched.
 * 2. The most recent `keepAllAudioNights` nights keep everything.
 * 3. Older nights drop full-night segments and keep only their `clipsPerOlderNight` best clips.
 * 4. If still over the limit, the oldest remaining clips go first.
 */
object StorageCleanup {

    /** [nights] must be newest first. */
    fun clipsToDelete(nights: List<SessionAudio>, settings: StorageSettings): Map<String, Set<String>> {
        val delete = mutableMapOf<String, MutableSet<String>>()
        fun mark(sessionId: String, clipId: String) = delete.getOrPut(sessionId) { mutableSetOf() }.add(clipId)

        nights.forEachIndexed { index, night ->
            if (night.protected || index < settings.keepAllAudioNights) return@forEachIndexed
            val candidates = night.clips.filterNot { it.pinned }
            candidates.filter { it.kind == ClipKind.SEGMENT }.forEach { mark(night.sessionId, it.id) }
            candidates.filter { it.kind != ClipKind.SEGMENT }
                .sortedByDescending { ClipRanking.priority(it) }
                .drop(settings.clipsPerOlderNight)
                .forEach { mark(night.sessionId, it.id) }
        }

        val limitBytes = settings.limitMb.toLong() * 1024 * 1024
        var total = nights.sumOf { n -> n.clips.filterNot { delete[n.sessionId]?.contains(it.id) == true }.sumOf { it.sizeBytes } }
        if (total > limitBytes) {
            // Oldest night first; within a night, least valuable first.
            for (night in nights.asReversed()) {
                if (night.protected) continue
                val remaining = night.clips
                    .filterNot { it.pinned || delete[night.sessionId]?.contains(it.id) == true }
                    .sortedBy { ClipRanking.priority(it) }
                for (clip in remaining) {
                    if (total <= limitBytes) break
                    mark(night.sessionId, clip.id)
                    total -= clip.sizeBytes
                }
                if (total <= limitBytes) break
            }
        }
        return delete
    }
}
