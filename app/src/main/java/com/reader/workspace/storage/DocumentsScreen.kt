package com.reader.workspace.storage

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun DocumentsScreen(
    onBack: () -> Unit,
    onOpenDocument: (VaultDocument) -> Unit,
) {
    val context = LocalContext.current
    val repository = remember(context.applicationContext) {
        DocumentVaultRepository.get(context.applicationContext)
    }
    val documents by repository.documents.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    var statusMessage by remember { mutableStateOf<String?>(null) }
    var importsRemaining by remember { mutableIntStateOf(0) }
    var pendingDelete by remember { mutableStateOf<VaultDocument?>(null) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult

        importsRemaining = uris.size
        statusMessage = null
        scope.launch {
            var imported = 0
            var failed = 0
            for (uri in uris) {
                try {
                    repository.importDocument(uri)
                    imported += 1
                } catch (_: Throwable) {
                    failed += 1
                } finally {
                    importsRemaining -= 1
                }
            }

            statusMessage = when {
                failed == 0 -> "$imported document(s) imported offline."
                imported == 0 -> "Import failed. The original files were not changed."
                else -> "$imported imported, $failed failed."
            }
        }
    }

    pendingDelete?.let { document ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete from Reader?") },
            text = {
                Text("This removes Reader's local copy of ${document.displayName}. The original file outside Reader is not changed.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        scope.launch {
                            try {
                                repository.deleteDocument(document.id)
                                statusMessage = "${document.displayName} deleted from Reader."
                            } catch (_: Throwable) {
                                statusMessage = "Could not delete ${document.displayName}."
                            }
                        }
                    },
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("Cancel")
                }
            },
        )
    }

    Scaffold(
        topBar = {
            DocumentsHeader(
                documentCount = documents.size,
                onBack = onBack,
                onImport = { importLauncher.launch(arrayOf("*/*")) },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (importsRemaining > 0) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    text = "Copying $importsRemaining document(s) into the local vault…",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            statusMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            if (documents.isEmpty() && importsRemaining == 0) {
                EmptyVault()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(documents, key = { it.id }) { document ->
                        VaultDocumentCard(
                            document = document,
                            onOpen = { onOpenDocument(document) },
                            onDelete = { pendingDelete = document },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DocumentsHeader(
    documentCount: Int,
    onBack: () -> Unit,
    onImport: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onBack) {
            Text("Back")
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Documents",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "$documentCount stored locally",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Button(onClick = onImport) {
            Text("Import")
        }
    }
}

@Composable
private fun EmptyVault() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Local vault is empty",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Import PDF, Office, text, image, or other document files. Reader copies them into its private app storage so they remain available offline.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun VaultDocumentCard(
    document: VaultDocument,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    val canOpen = VaultDisplay.isPdf(document)

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = document.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "${VaultDisplay.typeLabel(document)} · ${VaultDisplay.formatSize(document.sizeBytes)} · Offline",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onOpen, enabled = canOpen) {
                Text(if (canOpen) "Open" else "Viewer later")
            }
            TextButton(onClick = onDelete) {
                Text("Delete")
            }
        }
    }
}
