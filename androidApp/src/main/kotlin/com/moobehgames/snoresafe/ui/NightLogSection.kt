package com.moobehgames.snoresafe.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.moobehgames.snoresafe.R
import com.moobehgames.snoresafe.core.settings.WeightUnit
import com.moobehgames.snoresafe.core.tags.CustomTag
import com.moobehgames.snoresafe.core.tags.NightLog
import kotlin.math.roundToInt

private val REST_LABELS = listOf(R.string.rest_1, R.string.rest_2, R.string.rest_3, R.string.rest_4, R.string.rest_5)

/** The user's own notes on a night: remedies and factors, how rested they felt, weight, free text. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NightLogSection(log: NightLog, custom: List<CustomTag>, weightUnit: WeightUnit, onSave: (NightLog) -> Unit, onEditTags: () -> Unit) {
    var note by remember(log.sessionId, log.note) { mutableStateOf(log.note) }
    val shownWeight = log.weightKg?.let { formatWeight(it * weightUnit.perKg) } ?: ""
    var weight by remember(log.sessionId, shownWeight) { mutableStateOf(shownWeight) }
    val parsedWeight = weight.replace(',', '.').toFloatOrNull()?.div(weightUnit.perKg)
    val weightValid = weight.isBlank() || (parsedWeight != null && parsedWeight in 20f..400f)
    val dirty = note != log.note || weight != shownWeight

    Text(stringResource(R.string.remedies_and_factors), style = MaterialTheme.typography.titleMedium)
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (log.tags.isEmpty()) {
                Text(stringResource(R.string.tonight_none), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                TagChips(log.tags, custom, onEditTags)
            }
            TextButton(onClick = onEditTags) { Text(stringResource(R.string.edit)) }

            Text(stringResource(R.string.rest_rating), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                REST_LABELS.forEachIndexed { i, label ->
                    val value = i + 1
                    FilterChip(
                        selected = log.restRating == value,
                        onClick = { onSave(log.copy(restRating = if (log.restRating == value) null else value)) },
                        label = { Text(stringResource(label)) },
                    )
                }
            }

            OutlinedTextField(
                weight,
                { weight = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(6) },
                label = { Text(stringResource(R.string.weight)) },
                suffix = { Text(stringResource(if (weightUnit == WeightUnit.KG) R.string.unit_kg else R.string.unit_lb)) },
                isError = !weightValid,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                note,
                { note = it.take(1_000) },
                label = { Text(stringResource(R.string.notes)) },
                placeholder = { Text(stringResource(R.string.notes_hint)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            if (dirty) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("", Modifier.weight(1f))
                    TextButton(
                        onClick = { onSave(log.copy(note = note.trim(), weightKg = if (weight.isBlank()) null else parsedWeight)) },
                        enabled = weightValid,
                    ) { Text(stringResource(R.string.save)) }
                }
            }
        }
    }
}

private fun formatWeight(v: Float): String = ((v * 10).roundToInt() / 10f).let { if (it % 1f == 0f) it.toInt().toString() else it.toString() }
