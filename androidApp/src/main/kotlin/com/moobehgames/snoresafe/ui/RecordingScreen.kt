package com.moobehgames.snoresafe.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moobehgames.snoresafe.R
import com.moobehgames.snoresafe.recording.RecordingState
import com.moobehgames.snoresafe.ui.theme.BandColors

/** Deliberately dim: this screen is on while people fall asleep. */
@Composable
fun RecordingScreen(
    state: RecordingState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
) {
    var confirmStop by remember { mutableStateOf(false) }
    val active = state as? RecordingState.Active
    val dot by animateColorAsState(
        when {
            active == null || active.paused -> BandColors.quiet
            active.snoringNow -> BandColors.of(active.band)
            else -> BandColors.quiet
        },
        label = "dot",
    )
    val dim = Color(0xFF8C96AD)

    Surface(Modifier.fillMaxSize(), color = Color.Black) {
        Column(
            Modifier.safeDrawingPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(Modifier.size(20.dp).background(dot, CircleShape))
            Spacer(Modifier.height(16.dp))
            Text(
                when {
                    active == null -> stringResource(R.string.recording_starting)
                    active.paused -> stringResource(R.string.recording_paused)
                    active.snoringNow -> stringResource(R.string.recording_snoring_now)
                    else -> stringResource(R.string.recording_listening)
                },
                color = dim,
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(32.dp))
            Text(formatClock(active?.elapsedMs ?: 0L), color = dim, fontSize = 56.sp, fontWeight = FontWeight.Light)
            Text(stringResource(R.string.recording_elapsed), color = dim.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(24.dp))
            Text((active?.scoreSoFar ?: 0).toString(), color = dim, fontSize = 32.sp)
            Text(stringResource(R.string.recording_score_so_far), color = dim.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(48.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (active?.paused == true) {
                    OutlinedButton(onClick = onResume) { Text(stringResource(R.string.resume)) }
                } else {
                    OutlinedButton(onClick = onPause, enabled = active != null) { Text(stringResource(R.string.pause)) }
                }
                Button(onClick = { confirmStop = true }) { Text(stringResource(R.string.stop)) }
            }
        }
    }

    if (confirmStop) {
        AlertDialog(
            onDismissRequest = { confirmStop = false },
            title = { Text(stringResource(R.string.stop_confirm_title)) },
            text = { Text(stringResource(R.string.stop_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmStop = false
                    onStop()
                }) { Text(stringResource(R.string.stop)) }
            },
            dismissButton = { TextButton(onClick = { confirmStop = false }) { Text(stringResource(R.string.keep_listening)) } },
        )
    }
}
