package com.moobehgames.snoresafe.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.moobehgames.snoresafe.R
import com.moobehgames.snoresafe.ui.components.BackTopBar

private val FAQ = listOf(
    R.string.faq_q1 to R.string.faq_a1, R.string.faq_q2 to R.string.faq_a2, R.string.faq_q3 to R.string.faq_a3,
    R.string.faq_q4 to R.string.faq_a4, R.string.faq_q5 to R.string.faq_a5, R.string.faq_q6 to R.string.faq_a6,
    R.string.faq_q7 to R.string.faq_a7, R.string.faq_q8 to R.string.faq_a8, R.string.faq_q9 to R.string.faq_a9,
)

@Composable
fun FaqScreen(onBack: () -> Unit) = TextPage(stringResource(R.string.faq), onBack) {
    FAQ.forEach { (q, a) ->
        Text(stringResource(q), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(a), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 8.dp))
    }
}

@Composable
fun PrivacyScreen(onBack: () -> Unit) = TextPage(stringResource(R.string.privacy), onBack) {
    Text(stringResource(R.string.privacy_body), style = MaterialTheme.typography.bodyMedium)
    Text(stringResource(R.string.medical_disclaimer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun LicencesScreen(onBack: () -> Unit) = TextPage(stringResource(R.string.licences), onBack) {
    Text(stringResource(R.string.licences_body), style = MaterialTheme.typography.bodyMedium)
    Text("https://www.apache.org/licenses/LICENSE-2.0", style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun TextPage(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Scaffold(topBar = { BackTopBar(title, onBack) }) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) { content() }
    }
}
