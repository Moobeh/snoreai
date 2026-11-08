package com.moobehgames.snoresafe.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moobehgames.snoresafe.R
import com.moobehgames.snoresafe.core.analysis.SnoreBand
import com.moobehgames.snoresafe.core.session.EndReason
import com.moobehgames.snoresafe.core.session.SessionSummary
import com.moobehgames.snoresafe.ui.theme.BandColors
import kotlin.math.roundToInt

@Composable
fun ResultsScreen(session: SessionSummary, onBack: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { confirmDelete = true }) { Text(stringResource(R.string.delete)) }
            }
            Text(
                "${formatDate(context, session.startEpochMs)}  ${formatTime(context, session.startEpochMs)} – ${formatTime(context, session.endEpochMs)}",
                style = MaterialTheme.typography.titleMedium,
            )

            if (session.endReason == EndReason.RECOVERED) {
                Card { Text(stringResource(R.string.recovered_note, formatTime(context, session.endEpochMs)), Modifier.padding(16.dp)) }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(session.snoreScore.toString(), fontSize = 72.sp, fontWeight = FontWeight.Bold, color = scoreColor(session.snoreScore))
                Text(stringResource(R.string.snore_score), style = MaterialTheme.typography.titleSmall)
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Stat(stringResource(R.string.time_in_bed), formatDuration(session.timeInBedMs))
                Stat(stringResource(R.string.monitored), formatDuration(session.monitoredMs))
                Stat(stringResource(R.string.snoring), stringResource(R.string.percent_value, session.snoringPercent.roundToInt()))
            }

            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (band in listOf(SnoreBand.LIGHT, SnoreBand.LOUD, SnoreBand.EPIC)) BandRow(session, band)
                }
            }

            Text(stringResource(R.string.timeline), style = MaterialTheme.typography.titleMedium)
            Timeline(session)

            if (session.otherSoundMs.isNotEmpty()) {
                Text(stringResource(R.string.other_sounds), style = MaterialTheme.typography.titleMedium)
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        session.otherSoundMs.entries.sortedByDescending { it.value }.forEach { (sound, ms) ->
                            Row {
                                Text(soundLabel(sound), Modifier.weight(1f))
                                Text(formatDuration(ms))
                            }
                        }
                    }
                }
            }

            if (session.interruptions.isNotEmpty()) {
                Text(stringResource(R.string.interruptions), style = MaterialTheme.typography.titleMedium)
                session.interruptions.forEach {
                    Text(
                        stringResource(R.string.interruption_item, formatTime(context, it.startEpochMs), formatTime(context, it.endEpochMs)),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            Text(
                stringResource(R.string.medical_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_confirm_title)) },
            text = { Text(stringResource(R.string.delete_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BandRow(session: SessionSummary, band: SnoreBand) {
    val pct = session.bandPercent(band)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(bandLabel(band), Modifier.width(64.dp))
        LinearProgressIndicator(
            progress = { (pct / 100f).coerceIn(0f, 1f) },
            color = BandColors.of(band),
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.weight(1f).height(8.dp),
        )
        Text(
            stringResource(R.string.percent_value, pct.roundToInt()),
            Modifier.width(48.dp).padding(start = 8.dp),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** One bar per minute: height = share of the minute spent snoring, colour = loudest band. */
@Composable
private fun Timeline(session: SessionSummary) {
    val minutes = ((session.endEpochMs - session.startEpochMs) / 60_000L).toInt().coerceAtLeast(1)
    val byMinute = remember(session.id) { session.chart.associateBy { it.minuteIndex } }
    val description = stringResource(R.string.timeline)
    val track = MaterialTheme.colorScheme.surfaceVariant
    Canvas(
        Modifier.fillMaxWidth().height(120.dp).semantics { contentDescription = description },
    ) {
        drawRect(track, size = size)
        val barWidth = size.width / minutes
        for (m in 0 until minutes) {
            val p = byMinute[m] ?: continue
            if (p.band == SnoreBand.QUIET || p.snoreFraction <= 0f) continue
            val h = size.height * p.snoreFraction
            drawRect(
                color = BandColors.of(p.band),
                topLeft = Offset(m * barWidth, size.height - h),
                size = Size(maxOf(barWidth, 1f), h),
            )
        }
    }
}
