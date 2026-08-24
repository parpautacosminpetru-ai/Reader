package com.reader.workspace.research

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private enum class ResearchHubDestination {
    RULES,
    MARGINALIA_LINKS,
}

@Composable
fun ResearchHubScreen(onBack: () -> Unit) {
    var destination by remember { mutableStateOf<ResearchHubDestination?>(null) }

    when (destination) {
        ResearchHubDestination.RULES -> ResearchRulesWorkspaceScreen(
            onBack = { destination = null },
        )
        ResearchHubDestination.MARGINALIA_LINKS -> ResearchMarginaliaLinksScreen(
            onBack = { destination = null },
        )
        null -> ResearchHubHome(
            onBack = onBack,
            onOpenRules = { destination = ResearchHubDestination.RULES },
            onOpenMarginaliaLinks = { destination = ResearchHubDestination.MARGINALIA_LINKS },
        )
    }
}

@Composable
private fun ResearchHubHome(
    onBack: () -> Unit,
    onOpenRules: () -> Unit,
    onOpenMarginaliaLinks: () -> Unit,
) {
    Scaffold(
        modifier = Modifier.safeDrawingPadding(),
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TextButton(onClick = onBack) { Text("Back") }
                Text(
                    "Lexical Research",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Deterministic/offline research workspace",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenRules),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("Rules & Cartography", fontWeight = FontWeight.Bold)
                    Text(
                        "Axes, suffix/case/diacritics controls, sentence/paragraph/page rules, profiles, OCR and whole-document density maps.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text("Open Research 0.9", color = MaterialTheme.colorScheme.primary)
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenMarginaliaLinks),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("Research ↔ Marginalia", fontWeight = FontWeight.Bold)
                    Text(
                        "Pin lexical hits or structural intersections as persistent source anchors, then jump back to the exact page and offset.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text("Open anchor workspace", color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
