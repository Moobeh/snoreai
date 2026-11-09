package com.moobehgames.snoresafe.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.moobehgames.snoresafe.R
import com.moobehgames.snoresafe.core.audio.SessionAudio
import com.moobehgames.snoresafe.core.session.SessionSummary
import com.moobehgames.snoresafe.core.settings.AppSettings
import com.moobehgames.snoresafe.ui.components.BackTopBar
import com.moobehgames.snoresafe.ui.components.ChoiceRow
import com.moobehgames.snoresafe.ui.components.SectionHeader

@Composable
fun StorageScreen(
    settings: AppSettings,
    usedBytes: Long,
    freeBytes: Long,
    sessions: List<SessionSummary>,
    audio: List<SessionAudio>,
    update: ((AppSettings) -> AppSettings) -> Unit,
    onCleanUp: () -> Unit,
    onDeleteAudio: (String) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val audioById = audio.associateBy { it.sessionId }
    var confirmDeleteFor by remember { mutableStateOf<String?>(null) }
    confirmDeleteFor?.let { id ->
        AlertDialog(
            onDismissRequest = { confirmDeleteFor = null },
            title = { Text(stringResource(R.string.delete_audio_confirm_title)) },
            text = { Text(stringResource(R.string.delete_audio_confirm_body)) },
            confirmButton = { TextButton(onClick = { onDeleteAudio(id); confirmDeleteFor = null }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { confirmDeleteFor = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    Scaffold(topBar = { BackTopBar(stringResource(R.string.storage_title), onBack) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.storage_used, formatBytes(usedBytes)), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.storage_free, formatBytes(freeBytes)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            ChoiceRow(
                stringResource(R.string.storage_limit), AppSettings.STORAGE_LIMIT_CHOICES_MB, settings.storage.limitMb,
                label = { if (it >= 1_000) "${it / 1_000} GB" else "$it MB" },
                onSelect = { v -> update { it.copy(storage = it.storage.copy(limitMb = v)) } },
            )
            ChoiceRow(
                stringResource(R.string.keep_all_nights), AppSettings.KEEP_NIGHTS_CHOICES, settings.storage.keepAllAudioNights,
                label = { pluralStringResource(R.plurals.nights_value, it, it) },
                onSelect = { v -> update { it.copy(storage = it.storage.copy(keepAllAudioNights = v)) } },
            )
            ChoiceRow(
                stringResource(R.string.clips_older), AppSettings.CLIPS_PER_OLDER_NIGHT_CHOICES, settings.storage.clipsPerOlderNight,
                label = { pluralStringResource(R.plurals.clips_value, it, it) },
                onSelect = { v -> update { it.copy(storage = it.storage.copy(clipsPerOlderNight = v)) } },
            )
            Text(
                stringResource(R.string.storage_rules),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
            Button(onClick = onCleanUp, modifier = Modifier.padding(horizontal = 16.dp)) { Text(stringResource(R.string.clean_up_now)) }

            val withAudio = sessions.filter { (audioById[it.id]?.clips?.size ?: 0) > 0 }
            if (withAudio.isNotEmpty()) {
                SectionHeader(stringResource(R.string.nights_with_audio))
                withAudio.forEach { s ->
                    val a = audioById.getValue(s.id)
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(formatDate(context, s.startEpochMs), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                formatBytes(a.sizeBytes) + if (a.protected) " · " + stringResource(R.string.protected_label) else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { confirmDeleteFor = s.id }) { Text(stringResource(R.string.delete_audio)) }
                    }
                }
            }
        }
    }
}
