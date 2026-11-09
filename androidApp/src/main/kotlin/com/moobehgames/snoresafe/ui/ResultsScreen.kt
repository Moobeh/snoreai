package com.moobehgames.snoresafe.ui

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moobehgames.snoresafe.R
import com.moobehgames.snoresafe.core.analysis.SnoreBand
import com.moobehgames.snoresafe.core.audio.AudioClip
import com.moobehgames.snoresafe.core.audio.ClipKind
import com.moobehgames.snoresafe.core.audio.SessionAudio
import com.moobehgames.snoresafe.core.session.EndReason
import com.moobehgames.snoresafe.core.session.SessionSummary
import com.moobehgames.snoresafe.ui.components.BackTopBar
import com.moobehgames.snoresafe.ui.components.SwitchRow
import com.moobehgames.snoresafe.ui.theme.BandColors
import kotlin.math.roundToInt

@Composable
fun ResultsScreen(
    session: SessionSummary,
    audio: SessionAudio,
    log: com.moobehgames.snoresafe.core.tags.NightLog,
    customTags: List<com.moobehgames.snoresafe.core.tags.CustomTag>,
    weightUnit: com.moobehgames.snoresafe.core.settings.WeightUnit,
    onSaveLog: (com.moobehgames.snoresafe.core.tags.NightLog) -> Unit,
    onEditTags: () -> Unit,
    player: ClipPlayer,
    showTutorial: Boolean,
    clipFile: (AudioClip) -> java.io.File,
    onTutorialSeen: () -> Unit,
    onPin: (AudioClip, Boolean) -> Unit,
    onLabel: (AudioClip, com.moobehgames.snoresafe.core.audio.ClipLabel?) -> Unit,
    onDeleteClip: (AudioClip) -> Unit,
    onProtect: (Boolean) -> Unit,
    onShare: (withClips: Boolean) -> Unit,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    onBatteryGuide: () -> Unit = {},
    onShareReel: () -> Unit = {},
) {
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    var shareMenu by remember { mutableStateOf(false) }
    var confirmClipDelete by remember { mutableStateOf<AudioClip?>(null) }
    val playing by player.state.collectAsState()
    val nothingToPlay = stringResource(R.string.nothing_to_play)

    fun play(clip: AudioClip, atEpochMs: Long? = null) {
        val offset = if (atEpochMs != null && clip.kind == ClipKind.SEGMENT) (atEpochMs - clip.startEpochMs).toInt() else 0
        player.toggle(clip.id, clipFile(clip), offset)
    }

    Scaffold(topBar = { BackTopBar(formatDate(context, session.startEpochMs), onBack) }) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "${formatTime(context, session.startEpochMs)} – ${formatTime(context, session.endEpochMs)}",
                style = MaterialTheme.typography.titleMedium,
            )

            if (session.endReason == EndReason.RECOVERED) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f))) {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.recovered_note, formatTime(context, session.endEpochMs)))
                        TextButton(onClick = onBatteryGuide) { Text(stringResource(R.string.battery_guide_how)) }
                    }
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(session.snoreScore.toString(), fontSize = 72.sp, fontWeight = FontWeight.Bold, color = scoreColor(session.snoreScore))
                Text(stringResource(R.string.snore_score), style = MaterialTheme.typography.titleSmall)
            }

            if (showTutorial) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.tutorial_title), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.tutorial_body), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                        TextButton(onClick = onTutorialSeen) { Text(stringResource(R.string.got_it)) }
                    }
                }
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

            NightLogSection(log, customTags, weightUnit, onSaveLog, onEditTags)

            Text(stringResource(R.string.timeline), style = MaterialTheme.typography.titleMedium)
            Timeline(session, audio, onTap = { epoch ->
                val clip = audio.clipAt(epoch)
                if (clip == null) Toast.makeText(context, nothingToPlay, Toast.LENGTH_SHORT).show() else play(clip, epoch)
            })
            if (audio.clips.isNotEmpty()) {
                Text(stringResource(R.string.timeline_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            DisturbancesSection(session, audio, playing, ::play)

            SoundsSection(session, audio, playing, ::play)

            RecordingsSection(audio, playing, ::play, onPin, onLabel) { confirmClipDelete = it }

            if (session.interruptions.isNotEmpty()) {
                Text(stringResource(R.string.interruptions), style = MaterialTheme.typography.titleMedium)
                session.interruptions.forEach {
                    Text(
                        stringResource(R.string.interruption_item, formatTime(context, it.startEpochMs), formatTime(context, it.endEpochMs)),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            Card {
                SwitchRow(stringResource(R.string.protect_night), stringResource(R.string.protect_night_desc), audio.protected, onProtect)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box {
                    TextButton(onClick = { shareMenu = true }) {
                        Icon(Icons.Filled.Share, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.share_report))
                    }
                    DropdownMenu(expanded = shareMenu, onDismissRequest = { shareMenu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.share_report)) }, onClick = { shareMenu = false; onShare(false) })
                        if (audio.clips.any { it.pinned }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.share_report_with_clips)) }, onClick = { shareMenu = false; onShare(true) })
                        }
                        if (audio.clips.any { it.kind == ClipKind.SNORE || it.kind == ClipKind.SOUND }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.share_reel)) }, onClick = { shareMenu = false; onShareReel() })
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { confirmDelete = true }) { Text(stringResource(R.string.delete)) }
            }

            Text(
                stringResource(R.string.medical_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    confirmClipDelete?.let { clip ->
        AlertDialog(
            onDismissRequest = { confirmClipDelete = null },
            title = { Text(stringResource(R.string.clip_delete)) },
            text = { Text(stringResource(R.string.clip_delete_confirm_body)) },
            confirmButton = { TextButton(onClick = { onDeleteClip(clip); confirmClipDelete = null }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { confirmClipDelete = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_confirm_title)) },
            text = { Text(stringResource(R.string.delete_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    player.stop()
                    onDelete()
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun SoundsSection(session: SessionSummary, audio: SessionAudio, playing: ClipPlayer.State, play: (AudioClip, Long?) -> Unit) {
    val bySound = session.soundEventsBySound
    if (bySound.isEmpty() && session.otherSoundMs.isEmpty()) return
    val notable = bySound.filterKeys { it.notable }
    val common = (session.otherSoundMs.keys + bySound.keys).distinct().filterNot { it.notable }
        .sortedByDescending { session.otherSoundMs[it] ?: 0L }

    @Composable
    fun row(sound: com.moobehgames.snoresafe.core.analysis.NightSound) {
        val events = bySound[sound].orEmpty()
        val total = session.otherSoundMs[sound] ?: events.sumOf { it.durationMs }
        val best = audio.clips.filter { it.kind == ClipKind.SOUND && it.sound == sound }.maxByOrNull { it.peak }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text(soundLabel(sound), style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (events.isNotEmpty()) stringResource(R.string.sound_count, events.size, formatDuration(total)) else formatDuration(total),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (best != null) PlayButton(best, playing) { play(best, null) }
        }
    }

    if (notable.isNotEmpty()) {
        Text(stringResource(R.string.notable_sounds), style = MaterialTheme.typography.titleMedium)
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f))) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { notable.keys.forEach { row(it) } }
        }
    }
    if (common.isNotEmpty()) {
        Text(stringResource(R.string.other_sounds), style = MaterialTheme.typography.titleMedium)
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { common.forEach { row(it) } }
        }
    }
}

@Composable
private fun RecordingsSection(
    audio: SessionAudio,
    playing: ClipPlayer.State,
    play: (AudioClip, Long?) -> Unit,
    onPin: (AudioClip, Boolean) -> Unit,
    onLabel: (AudioClip, com.moobehgames.snoresafe.core.audio.ClipLabel?) -> Unit,
    onDelete: (AudioClip) -> Unit,
) {
    val context = LocalContext.current
    Text(stringResource(R.string.recordings), style = MaterialTheme.typography.titleMedium)
    if (audio.clips.isEmpty()) {
        Text(stringResource(R.string.no_recordings), color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    if (audio.clips.none { it.userLabel != null } && audio.clips.any { it.kind == ClipKind.SNORE }) {
        Text(stringResource(R.string.labels_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    val groups = listOf(
        R.string.recordings_snores to audio.clips.filter { it.kind == ClipKind.SNORE }.sortedByDescending { it.peak },
        R.string.recordings_sounds to audio.clips.filter { it.kind == ClipKind.SOUND }.sortedBy { it.startEpochMs },
        R.string.recordings_full_night to audio.clips.filter { it.kind == ClipKind.SEGMENT }.sortedBy { it.startEpochMs },
    )
    for ((title, clips) in groups) {
        if (clips.isEmpty()) continue
        Text(stringResource(title), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Card {
            Column(Modifier.padding(vertical = 4.dp)) {
                clips.forEach { clip ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        PlayButton(clip, playing) { play(clip, null) }
                        Column(Modifier.weight(1f)) {
                            Text(
                                when (clip.kind) {
                                    ClipKind.SOUND -> clip.sound?.let { soundLabel(it) } ?: ""
                                    ClipKind.SNORE -> bandLabel(SnoreBand.of(clip.peak, true))
                                    ClipKind.SEGMENT -> stringResource(R.string.recordings_full_night)
                                },
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                "${formatTime(context, clip.startEpochMs)} · ${formatSeconds(clip.durationMs)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            clip.userLabel?.let {
                                Text(
                                    stringResource(if (it == com.moobehgames.snoresafe.core.audio.ClipLabel.SNORING) R.string.labelled_snoring else R.string.labelled_not_snoring),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        if (clip.kind == ClipKind.SNORE) {
                            Box(Modifier.size(10.dp).background(BandColors.of(SnoreBand.of(clip.peak, true)), CircleShape))
                        }
                        if (clip.kind != ClipKind.SEGMENT && clip.features != null) LabelButton(clip) { onLabel(clip, it) }
                        IconButton(onClick = { onPin(clip, !clip.pinned) }) {
                            Icon(
                                if (clip.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                contentDescription = stringResource(if (clip.pinned) R.string.clip_unpin else R.string.clip_pin),
                            )
                        }
                        IconButton(onClick = { onDelete(clip) }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.clip_delete))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayButton(clip: AudioClip, state: ClipPlayer.State, onClick: () -> Unit) {
    val isPlaying = state.clipId == clip.id && state.playing
    IconButton(onClick = onClick) {
        Icon(
            if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = stringResource(if (isPlaying) R.string.clip_pause else R.string.clip_play),
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
            trackColor = MaterialTheme.colorScheme.background,
            gapSize = 0.dp,
            drawStopIndicator = {},
            modifier = Modifier.weight(1f).height(8.dp),
        )
        Text(
            stringResource(R.string.percent_value, pct.roundToInt()),
            Modifier.width(48.dp).padding(start = 8.dp),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** One bar per minute: height = share of the minute spent snoring, colour = loudest band. Dots mark notable sounds. */
@Composable
private fun Timeline(session: SessionSummary, audio: SessionAudio, onTap: (Long) -> Unit) {
    val minutes = ((session.endEpochMs - session.startEpochMs) / 60_000L).toInt().coerceAtLeast(1)
    val byMinute = remember(session.id) { session.chart.associateBy { it.minuteIndex } }
    val description = stringResource(R.string.timeline)
    val track = MaterialTheme.colorScheme.surfaceVariant
    val clipMark = MaterialTheme.colorScheme.primary
    val notableMark = MaterialTheme.colorScheme.error
    Canvas(
        Modifier.fillMaxWidth().height(120.dp)
            .semantics { contentDescription = description }
            .pointerInput(session.id, audio) {
                detectTapGestures { pos ->
                    val fraction = (pos.x / size.width).coerceIn(0f, 1f)
                    onTap(session.startEpochMs + (fraction * (session.endEpochMs - session.startEpochMs)).toLong())
                }
            },
    ) {
        drawRect(track, size = size)
        val span = (session.endEpochMs - session.startEpochMs).coerceAtLeast(1L).toFloat()
        val barWidth = size.width / minutes
        for (m in 0 until minutes) {
            val p = byMinute[m] ?: continue
            if (p.band == SnoreBand.QUIET || p.snoreFraction <= 0f) continue
            val h = (size.height - 10f) * p.snoreFraction
            drawRect(BandColors.of(p.band), topLeft = Offset(m * barWidth, size.height - h), size = Size(maxOf(barWidth, 1f), h))
        }
        audio.clips.filter { it.kind != ClipKind.SEGMENT }.forEach { c ->
            val x = (c.startEpochMs - session.startEpochMs) / span * size.width
            drawRect(clipMark, topLeft = Offset(x, 0f), size = Size(2f, 6f))
        }
        session.soundEvents.filter { it.sound.notable }.forEach { e ->
            val x = (e.startEpochMs - session.startEpochMs) / span * size.width
            drawCircle(notableMark, radius = 4f, center = Offset(x, 12f))
        }
    }
}

@Composable
private fun LabelButton(clip: AudioClip, onLabel: (com.moobehgames.snoresafe.core.audio.ClipLabel?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(
                if (clip.userLabel == null) Icons.AutoMirrored.Filled.HelpOutline else Icons.Filled.CheckCircle,
                contentDescription = stringResource(R.string.label_menu),
                tint = if (clip.userLabel == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Text(stringResource(R.string.label_menu), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            DropdownMenuItem(text = { Text(stringResource(R.string.label_snoring)) }, onClick = { open = false; onLabel(com.moobehgames.snoresafe.core.audio.ClipLabel.SNORING) })
            DropdownMenuItem(text = { Text(stringResource(R.string.label_not_snoring)) }, onClick = { open = false; onLabel(com.moobehgames.snoresafe.core.audio.ClipLabel.NOT_SNORING) })
            if (clip.userLabel != null) {
                DropdownMenuItem(text = { Text(stringResource(R.string.label_clear)) }, onClick = { open = false; onLabel(null) })
            }
        }
    }
}

/** Sounds that seemed to change the night, plus the bedroom's steady background noise. */
@Composable
private fun DisturbancesSection(session: SessionSummary, audio: SessionAudio, playing: ClipPlayer.State, play: (AudioClip, Long?) -> Unit) {
    val context = LocalContext.current
    val disturbances = remember(session.id) {
        com.moobehgames.snoresafe.core.insights.Disturbances.analyse(session)
            .filter { it.effect != com.moobehgames.snoresafe.core.insights.DisturbanceEffect.NO_CLEAR_CHANGE || it.event.sound.notable }
    }
    val background = remember(session.id) { com.moobehgames.snoresafe.core.insights.Disturbances.background(session) }
    if (disturbances.isEmpty() && background.isEmpty()) return

    if (disturbances.isNotEmpty()) {
        Text(stringResource(R.string.disturbances), style = MaterialTheme.typography.titleMedium)
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                disturbances.forEach { d ->
                    val clip = audio.clips.firstOrNull { it.kind == ClipKind.SOUND && it.sound == d.event.sound && it.covers(d.event.startEpochMs + 1_000) }
                        ?: audio.clips.firstOrNull { it.kind == ClipKind.SOUND && it.sound == d.event.sound && kotlin.math.abs(it.startEpochMs - d.event.startEpochMs) < 60_000 }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${formatTime(context, d.event.startEpochMs)} · ${soundLabel(d.event.sound)}", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                when (d.effect) {
                                    com.moobehgames.snoresafe.core.insights.DisturbanceEffect.SNORING_STOPPED ->
                                        stringResource(R.string.disturbance_stopped, formatDuration(d.quietMinutes * 60_000L))
                                    com.moobehgames.snoresafe.core.insights.DisturbanceEffect.SNORING_LOUDER -> stringResource(R.string.disturbance_louder)
                                    com.moobehgames.snoresafe.core.insights.DisturbanceEffect.NO_CLEAR_CHANGE -> stringResource(R.string.disturbance_none)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (clip != null) PlayButton(clip, playing) { play(clip, null) }
                    }
                }
            }
        }
    }
    if (background.isNotEmpty()) {
        Text(stringResource(R.string.bedroom_noise), style = MaterialTheme.typography.titleMedium)
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                background.forEach { b ->
                    Text(
                        stringResource(R.string.bedroom_noise_item, soundLabel(b.sound), formatDuration(b.ms), stringResource(R.string.percent_value, b.percentOfNight.roundToInt())),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                val sounds = background.map { it.sound }.toSet()
                val tip = when {
                    com.moobehgames.snoresafe.core.analysis.NightSound.MEDIA in sounds -> R.string.tip_media
                    com.moobehgames.snoresafe.core.analysis.NightSound.TRAFFIC in sounds -> R.string.tip_traffic
                    com.moobehgames.snoresafe.core.analysis.NightSound.FAN_OR_AC in sounds -> R.string.tip_fan
                    else -> null
                }
                if (tip != null) Text(stringResource(tip), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
