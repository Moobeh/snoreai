package com.moobehgames.snoresafe.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.moobehgames.snoresafe.BuildConfig
import com.moobehgames.snoresafe.R
import com.moobehgames.snoresafe.core.settings.AppSettings
import com.moobehgames.snoresafe.core.settings.RecordingMode
import com.moobehgames.snoresafe.core.support.SupportPolicy
import com.moobehgames.snoresafe.ui.components.ChoiceRow
import com.moobehgames.snoresafe.ui.components.SectionHeader
import com.moobehgames.snoresafe.ui.components.SettingRow
import com.moobehgames.snoresafe.ui.components.SwitchRow
import kotlin.math.roundToInt

class SettingsActions(
    val onStorage: () -> Unit,
    val onBackup: () -> Unit,
    val onRestore: () -> Unit,
    val onExportCsv: () -> Unit,
    val onExportJson: () -> Unit,
    val onSupport: () -> Unit,
    val onFaq: () -> Unit,
    val onPrivacy: () -> Unit,
    val onLicences: () -> Unit,
    val onShowIntro: () -> Unit,
)

@Composable
fun SettingsScreen(settings: AppSettings, update: ((AppSettings) -> AppSettings) -> Unit, actions: SettingsActions, modifier: Modifier = Modifier) {
    androidx.compose.material3.Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text(
            stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(16.dp),
        )

        SectionHeader(stringResource(R.string.section_detection))
        SliderSetting(
            title = stringResource(R.string.sensitivity),
            subtitle = stringResource(R.string.sensitivity_desc),
            value = settings.detection.sensitivity,
            range = 0f..1f,
            label = { stringResource(R.string.percent_value, ((it * 10).roundToInt() * 10)) },
            onDone = { v -> update { it.copy(detection = it.detection.copy(sensitivity = (v * 10).roundToInt() / 10f)) } },
        )
        SliderSetting(
            title = stringResource(R.string.volume_offset),
            subtitle = stringResource(R.string.volume_offset_desc),
            value = settings.detection.volumeOffsetDb,
            range = -15f..15f,
            label = { stringResource(R.string.db_value, it.roundToInt()) },
            onDone = { v -> update { it.copy(detection = it.detection.copy(volumeOffsetDb = v.roundToInt().toFloat())) } },
        )
        SwitchRow(stringResource(R.string.count_light), stringResource(R.string.count_light_desc), settings.detection.countLightSnoring) { on ->
            update { it.copy(detection = it.detection.copy(countLightSnoring = on)) }
        }

        SectionHeader(stringResource(R.string.section_recording))
        ChoiceRow(
            stringResource(R.string.recording_mode),
            RecordingMode.entries,
            settings.recordingMode,
            label = { modeLabel(it) },
            onSelect = { m -> update { it.copy(recordingMode = m) } },
        )
        Text(
            modeDescription(settings.recordingMode),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        SwitchRow(stringResource(R.string.save_other_clips), stringResource(R.string.save_other_clips_desc), settings.saveOtherSoundClips) { on ->
            update { it.copy(saveOtherSoundClips = on) }
        }
        ChoiceRow(
            stringResource(R.string.start_delay),
            AppSettings.START_DELAY_CHOICES,
            settings.startDelayMinutes,
            label = { if (it == 0) stringResource(R.string.none) else stringResource(R.string.minutes_value, it) },
            onSelect = { v -> update { it.copy(startDelayMinutes = v) } },
        )
        ChoiceRow(
            stringResource(R.string.auto_resume),
            AppSettings.AUTO_RESUME_CHOICES,
            settings.autoResumeMinutes,
            label = { if (it == 0) stringResource(R.string.never) else stringResource(R.string.minutes_value, it) },
            onSelect = { v -> update { it.copy(autoResumeMinutes = v) } },
        )

        ChoiceRow(
            stringResource(R.string.weight_unit),
            com.moobehgames.snoresafe.core.settings.WeightUnit.entries,
            settings.weightUnit,
            label = { stringResource(if (it == com.moobehgames.snoresafe.core.settings.WeightUnit.KG) R.string.unit_kg else R.string.unit_lb) },
            onSelect = { u -> update { it.copy(weightUnit = u) } },
        )

        SectionHeader(stringResource(R.string.section_storage))
        SettingRow(stringResource(R.string.storage_manage), onClick = actions.onStorage)

        SectionHeader(stringResource(R.string.section_data))
        SettingRow(stringResource(R.string.backup_create), stringResource(R.string.backup_create_desc), onClick = actions.onBackup)
        SettingRow(stringResource(R.string.backup_restore), onClick = actions.onRestore)
        SettingRow(stringResource(R.string.export_csv), onClick = actions.onExportCsv)
        SettingRow(stringResource(R.string.export_json), onClick = actions.onExportJson)

        if (SupportPolicy.showSettingsEntry(settings.support)) {
            SectionHeader(stringResource(R.string.section_support))
            SettingRow(stringResource(R.string.support_title), onClick = actions.onSupport)
        }

        SectionHeader(stringResource(R.string.section_help))
        SettingRow(stringResource(R.string.faq), onClick = actions.onFaq)
        SettingRow(stringResource(R.string.privacy), onClick = actions.onPrivacy)
        SettingRow(stringResource(R.string.show_intro), onClick = actions.onShowIntro)
        SettingRow(stringResource(R.string.licences), onClick = actions.onLicences)
        SettingRow(stringResource(R.string.about), stringResource(R.string.version_value, BuildConfig.VERSION_NAME))
        Text(
            stringResource(R.string.medical_disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
    }
    }
}

@Composable
fun modeLabel(m: RecordingMode) = stringResource(
    when (m) {
        RecordingMode.SNORE_CLIPS -> R.string.mode_snore_clips
        RecordingMode.ALL_SNORING -> R.string.mode_all_snoring
        RecordingMode.FULL_NIGHT -> R.string.mode_full_night
    },
)

@Composable
private fun modeDescription(m: RecordingMode) = stringResource(
    when (m) {
        RecordingMode.SNORE_CLIPS -> R.string.mode_snore_clips_desc
        RecordingMode.ALL_SNORING -> R.string.mode_all_snoring_desc
        RecordingMode.FULL_NIGHT -> R.string.mode_full_night_desc
    },
)

@Composable
private fun SliderSetting(
    title: String,
    subtitle: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    label: @Composable (Float) -> String,
    onDone: (Float) -> Unit,
) {
    var current by remember(value) { mutableFloatStateOf(value) }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        SettingRowInline(title, label(current))
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(value = current, onValueChange = { current = it }, valueRange = range, onValueChangeFinished = { onDone(current) })
    }
}

@Composable
private fun SettingRowInline(title: String, value: String) {
    androidx.compose.foundation.layout.Row {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
    }
}
