package com.moobehgames.snoresafe.ui

import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.moobehgames.snoresafe.R
import com.moobehgames.snoresafe.core.session.SessionSummary
import com.moobehgames.snoresafe.ui.theme.BandColors
import kotlin.math.roundToInt

@Composable
fun HomeScreen(
    sessions: List<SessionSummary>,
    permissionDenied: Boolean,
    startDelayMinutes: Int,
    showSupportCard: Boolean,
    tonightTags: Set<String>,
    customTags: List<com.moobehgames.snoresafe.core.tags.CustomTag>,
    onEditTonight: () -> Unit,
    batteryGuideSeen: Boolean,
    onBatteryGuide: () -> Unit,
    onStart: () -> Unit,
    onOpenSession: (String) -> Unit,
    onSupport: () -> Unit,
    onDismissSupport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.home_tagline), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                Button(onClick = onStart, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                    Text(
                        if (startDelayMinutes > 0) stringResource(R.string.start_session_delayed, startDelayMinutes) else stringResource(R.string.start_session),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Text(
                    stringResource(R.string.placement_tip),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            item { TonightCard(tonightTags, customTags, onEditTonight) }
            if (showSupportCard) item { SupportCard(onSupport, onDismissSupport) }
            if (permissionDenied) item { PermissionCard() }
            item { BatteryCard(batteryGuideSeen, onBatteryGuide) }
            item {
                Text(stringResource(R.string.past_nights), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            }
            if (sessions.isEmpty()) {
                item { Text(stringResource(R.string.no_sessions), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                items(sessions, key = { it.id }) { SessionRow(it) { onOpenSession(it.id) } }
            }
            item {
                Text(
                    stringResource(R.string.medical_disclaimer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }
        }
    }
}

@Composable
private fun SessionRow(s: SessionSummary, onClick: () -> Unit) {
    val context = LocalContext.current
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(formatDate(context, s.startEpochMs), style = MaterialTheme.typography.titleSmall)
                Text(
                    "${formatDuration(s.timeInBedMs)} · ${stringResource(R.string.snoring)} ${stringResource(R.string.percent_value, s.snoringPercent.roundToInt())}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                s.snoreScore.toString(),
                style = MaterialTheme.typography.headlineSmall,
                color = scoreColor(s.snoreScore),
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

fun scoreColor(score: Int) = when {
    score < 10 -> BandColors.light
    score < 40 -> BandColors.loud
    else -> BandColors.epic
}

@Composable
private fun PermissionCard() {
    val context = LocalContext.current
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.permission_needed), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.fromParts("package", context.packageName, null)),
                )
            }) { Text(stringResource(R.string.open_settings)) }
        }
    }
}

/** Shown until the user exempts SnoreSafe from battery optimisation. Opens settings; asks no special permission. */
@Composable
private fun BatteryCard(guideSeen: Boolean, onGuide: () -> Unit) {
    val context = LocalContext.current
    val power = remember { context.getSystemService(PowerManager::class.java) }
    var exempt by remember { mutableStateOf(true) }
    LifecycleResumeEffect(Unit) {
        exempt = power.isIgnoringBatteryOptimizations(context.packageName)
        onPauseOrDispose { }
    }
    val makerHasOwnSaver = remember { currentPhoneMaker() != com.moobehgames.snoresafe.core.device.PhoneMaker.OTHER }
    if (exempt && (guideSeen || !makerHasOwnSaver)) return
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.battery_title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.battery_body), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            Row {
                TextButton(onClick = onGuide) { Text(stringResource(R.string.battery_guide_how)) }
                if (!exempt) {
                    TextButton(onClick = {
                        runCatching { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
                    }) { Text(stringResource(R.string.battery_action)) }
                }
            }
        }
    }
}

@Composable
private fun SupportCard(onSupport: () -> Unit, onDismiss: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.support_card_title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.support_card_body), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            Row {
                TextButton(onClick = onSupport) { Text(stringResource(R.string.support_card_action)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.support_not_now)) }
            }
        }
    }
}

@Composable
private fun TonightCard(tags: Set<String>, custom: List<com.moobehgames.snoresafe.core.tags.CustomTag>, onEdit: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onEdit)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.tonight), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = onEdit) { Text(stringResource(R.string.edit)) }
            }
            if (tags.isEmpty()) {
                Text(stringResource(R.string.tonight_none), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                TagChips(tags, custom, onEdit)
            }
        }
    }
}
