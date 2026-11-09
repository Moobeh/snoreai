package com.moobehgames.snoresafe.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.moobehgames.snoresafe.R
import com.moobehgames.snoresafe.core.insights.RemedyTrial
import com.moobehgames.snoresafe.core.insights.RemedyTrials
import com.moobehgames.snoresafe.core.insights.TrialVerdict
import com.moobehgames.snoresafe.core.session.SessionSummary
import com.moobehgames.snoresafe.core.tags.CustomTag
import com.moobehgames.snoresafe.core.tags.TagCatalog
import com.moobehgames.snoresafe.core.tags.TagKind
import com.moobehgames.snoresafe.core.trends.Trends
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.roundToInt

/** The night that's coming up (or under way): evenings and mornings both point at "tonight". */
fun upcomingNightDay(nowMs: Long = System.currentTimeMillis()): Long =
    Trends.nightDay(nowMs + 12L * 60 * 60 * 1000, TimeZone.getDefault().getOffset(nowMs).toLong())

/** Progress and tonight's instruction for a running trial. */
@Composable
fun TrialCard(trial: RemedyTrial, custom: List<CustomTag>, onStop: () -> Unit) {
    val name = tagName(trial.tagId, custom) ?: return
    val tonight = upcomingNightDay()
    val use = trial.assignment(tonight)
    var confirmStop by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.trial_title, name), style = MaterialTheme.typography.titleSmall)
            val night = (tonight - trial.startNightDay + 1).toInt()
            Text(
                if (night < 1) stringResource(R.string.trial_waiting) else pluralStringResource(R.plurals.trial_progress, night, night.coerceAtMost(trial.nights), trial.nights),
                style = MaterialTheme.typography.bodySmall,
            )
            when (use) {
                true -> Text(stringResource(R.string.trial_tonight_use, name), style = MaterialTheme.typography.titleMedium)
                false -> Text(stringResource(R.string.trial_tonight_skip, name), style = MaterialTheme.typography.titleMedium)
                null -> Unit
            }
            TextButton(onClick = { confirmStop = true }) { Text(stringResource(R.string.trial_stop)) }
        }
    }
    if (confirmStop) {
        AlertDialog(
            onDismissRequest = { confirmStop = false },
            title = { Text(stringResource(R.string.trial_stop)) },
            text = { Text(stringResource(R.string.trial_stop_body)) },
            confirmButton = { TextButton(onClick = { confirmStop = false; onStop() }) { Text(stringResource(R.string.trial_stop)) } },
            dismissButton = { TextButton(onClick = { confirmStop = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
fun StartTrialDialog(custom: List<CustomTag>, onStart: (tagId: String, nights: Int) -> Unit, onDismiss: () -> Unit) {
    val remedies = TagCatalog.all(custom).filter { it.kind == TagKind.REMEDY }
    var picked by remember { mutableStateOf(remedies.first().id) }
    var nightsIndex by remember { mutableIntStateOf(1) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.trial_start)) },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.trial_start_body), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.trial_length), style = MaterialTheme.typography.titleSmall)
                androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RemedyTrials.LENGTH_CHOICES.forEachIndexed { i, n ->
                        FilterChip(selected = i == nightsIndex, onClick = { nightsIndex = i }, label = { Text(pluralStringResource(R.plurals.nights_choice, n, n)) })
                    }
                }
                Text(stringResource(R.string.trial_pick), style = MaterialTheme.typography.titleSmall)
                remedies.forEach { ref ->
                    FilterChip(selected = ref.id == picked, onClick = { picked = ref.id }, label = { Text(tagName(ref)) })
                }
            }
        },
        confirmButton = { TextButton(onClick = { onStart(picked, RemedyTrials.LENGTH_CHOICES[nightsIndex]) }) { Text(stringResource(R.string.trial_begin)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** The verdict of a finished (or running) trial, in plain words. */
@Composable
fun TrialResultText(trial: RemedyTrial, sessions: List<SessionSummary>, custom: List<CustomTag>) {
    val name = tagName(trial.tagId, custom) ?: return
    val offset = remember { TimeZone.getDefault().getOffset(System.currentTimeMillis()).toLong() }
    val r = remember(trial, sessions) { RemedyTrials.result(trial, sessions, offset) }
    val change = abs(r.change).roundToInt().toString()
    Text(
        when (r.verdict) {
            TrialVerdict.HELPS -> stringResource(R.string.trial_result_helps, name, change)
            TrialVerdict.MAKES_WORSE -> stringResource(R.string.trial_result_worse, name, change)
            TrialVerdict.NO_CLEAR_DIFFERENCE ->
                stringResource(R.string.trial_result_none, name, r.averageWith.roundToInt().toString(), r.averageWithout.roundToInt().toString())
            TrialVerdict.TOO_FEW_NIGHTS -> stringResource(R.string.trial_result_few, name)
        },
        style = MaterialTheme.typography.bodyMedium,
    )
}
