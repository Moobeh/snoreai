package com.moobehgames.snoresafe.core.export

import com.moobehgames.snoresafe.core.analysis.NightSound
import com.moobehgames.snoresafe.core.analysis.SnoreBand
import com.moobehgames.snoresafe.core.session.SessionSummary
import kotlin.math.round

/** Spreadsheet-friendly export of nights. Times are formatted by the caller (local time). */
object CsvExport {
    private val HEADER = listOf(
        "start", "end", "time_in_bed_min", "monitored_min", "snoring_min", "snoring_percent", "snore_score",
        "light_percent", "loud_percent", "epic_percent", "interruptions", "notable_sounds", "other_sounds", "recovered",
    )

    fun sessions(sessions: List<SessionSummary>, formatTime: (Long) -> String): String = buildString {
        appendLine(HEADER.joinToString(","))
        for (s in sessions.sortedBy { it.startEpochMs }) {
            val notable = s.soundEvents.filter { it.sound.notable }.groupingBy { it.sound }.eachCount()
            val other = s.otherSoundMs.entries.sortedByDescending { it.value }
            appendLine(
                listOf(
                    formatTime(s.startEpochMs),
                    formatTime(s.endEpochMs),
                    minutes(s.timeInBedMs),
                    minutes(s.monitoredMs),
                    minutes(s.snoringMs),
                    oneDecimal(s.snoringPercent),
                    s.snoreScore.toString(),
                    oneDecimal(s.bandPercent(SnoreBand.LIGHT)),
                    oneDecimal(s.bandPercent(SnoreBand.LOUD)),
                    oneDecimal(s.bandPercent(SnoreBand.EPIC)),
                    s.interruptions.size.toString(),
                    notable.entries.joinToString("; ") { "${label(it.key)} x${it.value}" },
                    other.joinToString("; ") { "${label(it.key)} ${minutes(it.value)}m" },
                    (s.endReason.name == "RECOVERED").toString(),
                ).joinToString(",") { escape(it) },
            )
        }
    }

    private fun label(s: NightSound) = s.name.lowercase().replace('_', ' ')
    private fun minutes(ms: Long) = (ms / 60_000).toString()
    private fun oneDecimal(v: Float) = (round(v * 10) / 10).toString()

    internal fun escape(v: String): String {
        // Leading =,+,-,@ would be run as a formula by spreadsheet apps.
        val safe = if (v.isNotEmpty() && v[0] in "=+-@" && v.toDoubleOrNull() == null) "'$v" else v
        return if (safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
    }
}
