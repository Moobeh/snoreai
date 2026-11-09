package com.moobehgames.snoresafe.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.moobehgames.snoresafe.R
import com.moobehgames.snoresafe.core.tags.BuiltInTag
import com.moobehgames.snoresafe.core.tags.CustomTag
import com.moobehgames.snoresafe.core.tags.TagCatalog
import com.moobehgames.snoresafe.core.tags.TagKind
import com.moobehgames.snoresafe.core.tags.TagRef

private fun builtInRes(tag: BuiltInTag): Pair<Int, Int> = when (tag) {
    BuiltInTag.MOUTHGUARD -> R.string.tag_mouthguard to R.string.tag_mouthguard_desc
    BuiltInTag.NASAL_STRIP -> R.string.tag_nasal_strip to R.string.tag_nasal_strip_desc
    BuiltInTag.NASAL_DILATOR -> R.string.tag_nasal_dilator to R.string.tag_nasal_dilator_desc
    BuiltInTag.NASAL_SPRAY -> R.string.tag_nasal_spray to R.string.tag_nasal_spray_desc
    BuiltInTag.SALINE_RINSE -> R.string.tag_saline_rinse to R.string.tag_saline_rinse_desc
    BuiltInTag.SIDE_SLEEPING -> R.string.tag_side_sleeping to R.string.tag_side_sleeping_desc
    BuiltInTag.POSITIONAL_DEVICE -> R.string.tag_positional_device to R.string.tag_positional_device_desc
    BuiltInTag.RAISED_HEAD -> R.string.tag_raised_head to R.string.tag_raised_head_desc
    BuiltInTag.ANTI_SNORE_PILLOW -> R.string.tag_anti_snore_pillow to R.string.tag_anti_snore_pillow_desc
    BuiltInTag.MOUTH_TAPE -> R.string.tag_mouth_tape to R.string.tag_mouth_tape_desc
    BuiltInTag.CHIN_STRAP -> R.string.tag_chin_strap to R.string.tag_chin_strap_desc
    BuiltInTag.THROAT_EXERCISES -> R.string.tag_throat_exercises to R.string.tag_throat_exercises_desc
    BuiltInTag.HUMIDIFIER -> R.string.tag_humidifier to R.string.tag_humidifier_desc
    BuiltInTag.AIR_PURIFIER -> R.string.tag_air_purifier to R.string.tag_air_purifier_desc
    BuiltInTag.ALLERGY_MEDICATION -> R.string.tag_allergy_medication to R.string.tag_allergy_medication_desc
    BuiltInTag.CPAP -> R.string.tag_cpap to R.string.tag_cpap_desc
    BuiltInTag.ALCOHOL -> R.string.tag_alcohol to R.string.tag_alcohol_desc
    BuiltInTag.LATE_MEAL -> R.string.tag_late_meal to R.string.tag_late_meal_desc
    BuiltInTag.CAFFEINE -> R.string.tag_caffeine to R.string.tag_caffeine_desc
    BuiltInTag.BLOCKED_NOSE -> R.string.tag_blocked_nose to R.string.tag_blocked_nose_desc
    BuiltInTag.ILL -> R.string.tag_ill to R.string.tag_ill_desc
    BuiltInTag.SMOKED -> R.string.tag_smoked to R.string.tag_smoked_desc
    BuiltInTag.SLEEPING_PILL -> R.string.tag_sleeping_pill to R.string.tag_sleeping_pill_desc
    BuiltInTag.VERY_TIRED -> R.string.tag_very_tired to R.string.tag_very_tired_desc
    BuiltInTag.EXERCISED -> R.string.tag_exercised to R.string.tag_exercised_desc
    BuiltInTag.STRESSED -> R.string.tag_stressed to R.string.tag_stressed_desc
    BuiltInTag.PERIOD -> R.string.tag_period to R.string.tag_period_desc
    BuiltInTag.NOT_MY_BED -> R.string.tag_not_my_bed to R.string.tag_not_my_bed_desc
}

fun tagNameRes(tag: BuiltInTag): Int = builtInRes(tag).first

@Composable
fun tagName(ref: TagRef): String = ref.builtIn?.let { stringResource(builtInRes(it).first) } ?: ref.customName.orEmpty()

@Composable
fun tagName(id: String, custom: List<CustomTag>): String? = TagCatalog.find(id, custom)?.let { tagName(it) }

/** Non-composable name lookup, for exports and PDFs. */
fun tagName(context: android.content.Context, id: String, custom: List<CustomTag>): String? {
    val ref = TagCatalog.find(id, custom) ?: return null
    return ref.builtIn?.let { context.getString(builtInRes(it).first) } ?: ref.customName
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagChips(ids: Set<String>, custom: List<CustomTag>, onClick: () -> Unit) {
    val refs = TagCatalog.all(custom).filter { it.id in ids }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        refs.forEach { AssistChip(onClick = onClick, label = { Text(tagName(it)) }) }
    }
}

/** Pick remedies and factors; users can add their own. */
@Composable
fun TagPickerDialog(
    selected: Set<String>,
    custom: List<CustomTag>,
    onAddCustom: (CustomTag) -> Unit,
    onDeleteCustom: (String) -> Unit,
    onDismiss: () -> Unit,
    onDone: (Set<String>) -> Unit,
) {
    var picked by remember { mutableStateOf(selected) }
    var adding by remember { mutableStateOf<TagKind?>(null) }
    val all = TagCatalog.all(custom)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.remedies_and_factors)) },
        text = {
            LazyColumn(Modifier.heightIn(max = 480.dp)) {
                for (kind in TagKind.entries) {
                    item(key = "h-$kind") {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
                            Text(
                                stringResource(if (kind == TagKind.REMEDY) R.string.remedies else R.string.factors),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { adding = kind }) {
                                Text(stringResource(if (kind == TagKind.REMEDY) R.string.add_remedy else R.string.add_factor))
                            }
                        }
                    }
                    items(all.filter { it.kind == kind }, key = { it.id }) { ref ->
                        val on = ref.id in picked
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable(role = Role.Checkbox) { picked = if (on) picked - ref.id else picked + ref.id }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = on, onCheckedChange = null)
                            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                                Text(tagName(ref), style = MaterialTheme.typography.bodyLarge)
                                ref.builtIn?.let {
                                    Text(
                                        stringResource(builtInRes(it).second),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            if (ref.builtIn == null) {
                                val name = tagName(ref)
                                IconButton(onClick = {
                                    picked = picked - ref.id
                                    onDeleteCustom(ref.id)
                                }) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.delete_custom_tag, name)) }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onDone(picked) }) { Text(stringResource(R.string.done)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )

    adding?.let { kind ->
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { adding = null },
            title = { Text(stringResource(if (kind == TagKind.REMEDY) R.string.add_remedy else R.string.add_factor)) },
            text = {
                OutlinedTextField(
                    name,
                    { name = it.take(TagCatalog.MAX_CUSTOM_NAME) },
                    label = { Text(stringResource(R.string.name_label)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val tag = CustomTag("c:" + java.util.UUID.randomUUID(), kind, name.trim())
                        onAddCustom(tag)
                        picked = picked + tag.id
                        adding = null
                    },
                    enabled = name.isNotBlank(),
                ) { Text(stringResource(R.string.add)) }
            },
            dismissButton = { TextButton(onClick = { adding = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
