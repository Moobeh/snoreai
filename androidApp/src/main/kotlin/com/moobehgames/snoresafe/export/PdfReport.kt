package com.moobehgames.snoresafe.export

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.moobehgames.snoresafe.R
import com.moobehgames.snoresafe.core.analysis.SnoreBand
import com.moobehgames.snoresafe.core.session.SessionSummary
import com.moobehgames.snoresafe.core.trends.Trends
import com.moobehgames.snoresafe.ui.formatDate
import com.moobehgames.snoresafe.ui.formatDuration
import com.moobehgames.snoresafe.ui.formatTime
import com.moobehgames.snoresafe.ui.soundLabelRes
import java.io.File
import java.util.TimeZone
import kotlin.math.roundToInt

/** Printable A4 reports for a doctor or dentist, drawn on the phone. */
object PdfReport {
    private const val W = 595
    private const val H = 842
    private const val M = 48f

    private val bandColors = mapOf(
        SnoreBand.LIGHT to Color.rgb(0x5F, 0xA8, 0x8A),
        SnoreBand.LOUD to Color.rgb(0xE0, 0x9A, 0x3A),
        SnoreBand.EPIC to Color.rgb(0xC9, 0x4C, 0x42),
    )

    private class Pen(context: Context) {
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 20f; typeface = Typeface.DEFAULT_BOLD; color = Color.BLACK }
        val h2 = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 13f; typeface = Typeface.DEFAULT_BOLD; color = Color.BLACK }
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10.5f; color = Color.rgb(0x22, 0x22, 0x22) }
        val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 8.5f; color = Color.rgb(0x55, 0x55, 0x55) }
        val big = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 40f; typeface = Typeface.DEFAULT_BOLD; color = Color.BLACK }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        val disclaimer = context.getString(R.string.medical_disclaimer)
    }

    fun night(context: Context, s: SessionSummary, out: File): File {
        val doc = PdfDocument()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, 1).create())
        val c = page.canvas
        val p = Pen(context)
        var y = M + 10

        c.drawText(context.getString(R.string.pdf_night_title), M, y, p.title); y += 22
        c.drawText("${formatDate(context, s.startEpochMs)}  ${formatTime(context, s.startEpochMs)} – ${formatTime(context, s.endEpochMs)}", M, y, p.body); y += 50

        c.drawText(s.snoreScore.toString(), M, y, p.big)
        c.drawText(context.getString(R.string.snore_score), M + 90, y - 14, p.h2)
        y += 30
        val rows = listOf(
            context.getString(R.string.time_in_bed) to formatDuration(s.timeInBedMs),
            context.getString(R.string.monitored) to formatDuration(s.monitoredMs),
            context.getString(R.string.snoring) to "${formatDuration(s.snoringMs)} (${s.snoringPercent.roundToInt()}%)",
            context.getString(R.string.band_light) to "${s.bandPercent(SnoreBand.LIGHT).roundToInt()}%",
            context.getString(R.string.band_loud) to "${s.bandPercent(SnoreBand.LOUD).roundToInt()}%",
            context.getString(R.string.band_epic) to "${s.bandPercent(SnoreBand.EPIC).roundToInt()}%",
        )
        rows.forEach { (k, v) -> c.drawText(k, M, y, p.body); c.drawText(v, M + 160, y, p.body); y += 16 }
        y += 16

        c.drawText(context.getString(R.string.timeline), M, y, p.h2); y += 10
        drawTimeline(c, s, M, y, W - 2 * M, 90f, p); y += 104
        c.drawText(formatTime(context, s.startEpochMs), M, y, p.small)
        val endLabel = formatTime(context, s.endEpochMs)
        c.drawText(endLabel, W - M - p.small.measureText(endLabel), y, p.small)
        y += 28

        if (s.soundEvents.isNotEmpty() || s.otherSoundMs.isNotEmpty()) {
            c.drawText(context.getString(R.string.other_sounds), M, y, p.h2); y += 18
            val counts = s.soundEvents.groupingBy { it.sound }.eachCount()
            val sounds = (s.otherSoundMs.keys + counts.keys).distinct()
                .sortedWith(compareByDescending<com.moobehgames.snoresafe.core.analysis.NightSound> { it.notable }.thenByDescending { s.otherSoundMs[it] ?: 0L })
            for (sound in sounds.take(14)) {
                val label = context.getString(soundLabelRes(sound))
                val count = counts[sound]?.let { "$it× · " } ?: ""
                c.drawText((if (sound.notable) "• " else "") + label, M, y, p.body)
                c.drawText(count + formatDuration(s.otherSoundMs[sound] ?: 0L), M + 220, y, p.body)
                y += 15
            }
            y += 12
        }

        if (s.interruptions.isNotEmpty()) {
            c.drawText(context.getString(R.string.interruptions), M, y, p.h2); y += 18
            s.interruptions.take(6).forEach {
                c.drawText(context.getString(R.string.interruption_item, formatTime(context, it.startEpochMs), formatTime(context, it.endEpochMs)), M, y, p.body)
                y += 15
            }
        }

        footer(c, context, p)
        doc.finishPage(page)
        return write(doc, out)
    }

    fun trend(context: Context, sessions: List<SessionSummary>, out: File): File {
        val doc = PdfDocument()
        val p = Pen(context)
        val offset = TimeZone.getDefault().getOffset(System.currentTimeMillis()).toLong()
        val recent = sessions.sortedByDescending { it.startEpochMs }.take(60)
        var pageNo = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
        var c = page.canvas
        var y = M + 10
        c.drawText(context.getString(R.string.pdf_trend_title), M, y, p.title); y += 26

        Trends.stats(recent)?.let { st ->
            listOf(
                context.getString(R.string.nights_count) to st.nights.toString(),
                context.getString(R.string.avg_score) to st.averageScore.roundToInt().toString(),
                context.getString(R.string.avg_snoring) to "${st.averageSnoringPercent.roundToInt()}%",
                context.getString(R.string.avg_loud_epic) to "${st.averageLoudOrEpicPercent.roundToInt()}%",
                context.getString(R.string.avg_time_in_bed) to formatDuration(st.averageTimeInBedMs),
            ).forEach { (k, v) -> c.drawText(k, M, y, p.body); c.drawText(v, M + 180, y, p.body); y += 16 }
        }
        y += 14
        val points = Trends.nightPoints(recent, offset)
        if (points.isNotEmpty()) {
            val chartH = 120f
            val maxScore = maxOf(40, points.maxOf { it.score })
            val bw = (W - 2 * M) / points.size
            p.fill.color = Color.rgb(0xF0, 0xF2, 0xF6)
            c.drawRect(M, y, W - M, y + chartH, p.fill)
            points.forEachIndexed { i, pt ->
                val h = chartH * pt.score / maxScore
                p.fill.color = when {
                    pt.score < 10 -> bandColors.getValue(SnoreBand.LIGHT)
                    pt.score < 40 -> bandColors.getValue(SnoreBand.LOUD)
                    else -> bandColors.getValue(SnoreBand.EPIC)
                }
                c.drawRect(M + i * bw + 1, y + chartH - h, M + (i + 1) * bw - 1, y + chartH, p.fill)
            }
            y += chartH + 24
        }

        val cols = floatArrayOf(M, M + 120, M + 200, M + 280, M + 350, M + 420)
        fun headerRow() {
            listOf("", context.getString(R.string.snore_score), context.getString(R.string.time_in_bed), context.getString(R.string.snoring),
                context.getString(R.string.band_loud), context.getString(R.string.band_epic))
                .forEachIndexed { i, t -> c.drawText(t, cols[i], y, p.h2) }
            y += 16
        }
        headerRow()
        for (s in recent) {
            if (y > H - 70) {
                footer(c, context, p)
                doc.finishPage(page)
                page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, ++pageNo).create())
                c = page.canvas
                y = M + 10
                headerRow()
            }
            listOf(
                formatDate(context, s.startEpochMs),
                s.snoreScore.toString(),
                formatDuration(s.timeInBedMs),
                "${s.snoringPercent.roundToInt()}%",
                "${s.bandPercent(SnoreBand.LOUD).roundToInt()}%",
                "${s.bandPercent(SnoreBand.EPIC).roundToInt()}%",
            ).forEachIndexed { i, t -> c.drawText(t, cols[i], y, p.body) }
            y += 14
        }
        footer(c, context, p)
        doc.finishPage(page)
        return write(doc, out)
    }

    private fun drawTimeline(c: Canvas, s: SessionSummary, x: Float, y: Float, w: Float, h: Float, p: Pen) {
        p.fill.color = Color.rgb(0xF0, 0xF2, 0xF6)
        c.drawRect(x, y, x + w, y + h, p.fill)
        val minutes = ((s.endEpochMs - s.startEpochMs) / 60_000L).toInt().coerceAtLeast(1)
        val bw = w / minutes
        for (pt in s.chart) {
            val color = bandColors[pt.band] ?: continue
            if (pt.snoreFraction <= 0f) continue
            p.fill.color = color
            val bh = h * pt.snoreFraction
            c.drawRect(x + pt.minuteIndex * bw, y + h - bh, x + (pt.minuteIndex + 1) * bw, y + h, p.fill)
        }
        p.fill.color = Color.rgb(0xC9, 0x4C, 0x42)
        val span = (s.endEpochMs - s.startEpochMs).coerceAtLeast(1L).toFloat()
        s.soundEvents.filter { it.sound.notable }.forEach { e ->
            c.drawCircle(x + (e.startEpochMs - s.startEpochMs) / span * w, y + 6, 3f, p.fill)
        }
    }

    private fun footer(c: Canvas, context: Context, p: Pen) {
        val now = System.currentTimeMillis()
        c.drawText(context.getString(R.string.pdf_generated, "${formatDate(context, now)} ${formatTime(context, now)}"), M, H - 44f, p.small)
        wrap(p.disclaimer, p.small, W - 2 * M).forEachIndexed { i, line -> c.drawText(line, M, H - 30f + i * 11, p.small) }
    }

    private fun wrap(text: String, paint: Paint, width: Float): List<String> {
        val lines = mutableListOf<String>()
        var line = ""
        for (word in text.split(' ')) {
            val next = if (line.isEmpty()) word else "$line $word"
            if (paint.measureText(next) > width && line.isNotEmpty()) {
                lines += line
                line = word
            } else {
                line = next
            }
        }
        if (line.isNotEmpty()) lines += line
        return lines
    }

    private fun write(doc: PdfDocument, out: File): File {
        out.parentFile?.mkdirs()
        out.outputStream().use { doc.writeTo(it) }
        doc.close()
        return out
    }
}
