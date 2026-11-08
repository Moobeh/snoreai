package com.moobehgames.snoresafe.ui

import android.content.Intent
import android.provider.Settings
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.moobehgames.snoresafe.R

@Composable
fun OnboardingScreen(permissionsGranted: Boolean, onRequestPermissions: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    var page by rememberSaveable { mutableIntStateOf(0) }
    val pages = listOf(
        R.string.onb_1_title to R.string.onb_1_body,
        R.string.onb_2_title to R.string.onb_2_body,
        R.string.onb_3_title to R.string.onb_3_body,
        R.string.onb_4_title to R.string.onb_4_body,
    )
    val last = page == pages.lastIndex

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.safeDrawingPadding().padding(24.dp)) {
            Row {
                Spacer(Modifier.weight(1f))
                if (!last) TextButton(onClick = onDone) { Text(stringResource(R.string.skip)) }
            }
            Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                androidx.compose.foundation.Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(120.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                )
                Spacer(Modifier.height(32.dp))
                Text(stringResource(pages[page].first), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                Text(stringResource(pages[page].second), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(24.dp))
                when (page) {
                    2 -> OutlinedButton(onClick = onRequestPermissions, enabled = !permissionsGranted) {
                        Text(stringResource(if (permissionsGranted) R.string.onb_3_granted else R.string.onb_3_action))
                    }
                    3 -> OutlinedButton(onClick = {
                        runCatching { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
                    }) { Text(stringResource(R.string.battery_action)) }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                pages.indices.forEach { i ->
                    Box(
                        Modifier.padding(end = 6.dp).size(8.dp)
                            .background(if (i == page) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                    )
                }
                Spacer(Modifier.weight(1f))
                Button(onClick = { if (last) onDone() else page++ }) {
                    Text(stringResource(if (last) R.string.get_started else R.string.next))
                }
            }
        }
    }
}
