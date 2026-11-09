package com.moobehgames.snoresafe.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.moobehgames.snoresafe.R
import com.moobehgames.snoresafe.billing.TipJar
import com.moobehgames.snoresafe.ui.components.BackTopBar

@Composable
fun SupportScreen(state: TipJar.State, supported: Boolean, onTip: (TipJar.Tip) -> Unit, onAlreadySupported: () -> Unit, onBack: () -> Unit) {
    Scaffold(topBar = { BackTopBar(stringResource(R.string.support_title), onBack) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.support_body), style = MaterialTheme.typography.bodyLarge)
            when {
                supported || state is TipJar.State.Thanks -> Text(stringResource(R.string.support_thanks), style = MaterialTheme.typography.titleMedium)
                state is TipJar.State.Loading -> Text(stringResource(R.string.tips_loading))
                state is TipJar.State.Unavailable -> Text(stringResource(R.string.tips_unavailable), color = MaterialTheme.colorScheme.onSurfaceVariant)
                state is TipJar.State.Ready -> TipJar.Tip.entries.forEach { tip ->
                    val price = state.prices[tip] ?: return@forEach
                    val label = stringResource(
                        when (tip) {
                            TipJar.Tip.COFFEE -> R.string.tip_coffee
                            TipJar.Tip.LUNCH -> R.string.tip_lunch
                            TipJar.Tip.THANKS -> R.string.tip_thanks
                        },
                    )
                    if (tip == TipJar.Tip.COFFEE) {
                        Button(onClick = { onTip(tip) }, Modifier.fillMaxWidth().height(52.dp)) { Text("$label · $price") }
                    } else {
                        OutlinedButton(onClick = { onTip(tip) }, Modifier.fillMaxWidth().height(52.dp)) { Text("$label · $price") }
                    }
                }
            }
            if (!supported && state !is TipJar.State.Thanks) {
                TextButton(onClick = onAlreadySupported) { Text(stringResource(R.string.already_supported)) }
            }
        }
    }
}
