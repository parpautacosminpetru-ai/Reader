package com.reader.workspace.index

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.reader.workspace.marginalia.MarginaliaRepository
import com.reader.workspace.storage.DocumentVaultRepository
import com.reader.workspace.storage.VaultDisplay
import com.reader.workspace.storage.VaultDocument
import java.util.UUID
import kotlinx.coroutines.launch

@Composable
fun IndexWorkspaceScreen(
    onBack: () -> Unit,
    onOpenPdf: (VaultDocument, Int) -> Unit,
) {
    val context = LocalContext.current
    val vaultRepository = remember(context.applicationContext) {
        DocumentVaultRepository.get(context.applicationContext)
    }
    val indexRepository = remember(context.applicationContext) {
        IndexRepository.get(context.applicationContext)
    }
    val marginaliaRepository = remember(context.applicationContext) {
        MarginaliaRepository.get(context.applicationContext)
    }

    val documents by vaultRepository.documents.collectAsState(initial = emptyList())
    val manualEntries by indexRepository.entries.collectAsState(initial = emptyList())
    val marginaliaItems by marginaliaRepository.indexableItems.collectAsState(initial = emptyList())

    val allItems = remember(manualEntries, marginaliaItems) {
        UniversalIndexComposer.compose(manualEntries, marginaliaItems)
    }
    val documentById = remember(documents) { documents.associateBy(VaultDocument::id) }

    var query by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    val categories = remember(allItems) {
        allItems.map(UniversalIndexItem::category)
            .distinctBy(String::lowercase)
            .sortedBy(String::lowercase)
    }
    LaunchedEffect(categories) {
        if (selectedCategory != null && categories.none { it.equals(selectedCategory, ignoreCase = true) }) {
            selectedCategory = null
        }
    }

    val filtered = remember(allItems, query, selectedCategory) {
        UniversalIndexComposer.filter(allItems, query, selectedCategory)
    }
    val grouped = remember(filtered) {
        filtered.groupBy { UniversalIndexComposer.alphabetBucket(it.title) }
    }

    Scaffold(
        modifier = Modifier.safeDrawingPadding(),
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack) { Text("Back") }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Universal Index", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "${allItems.size} entries · ${manualEntries.size} manual · ${marginaliaItems.size} derived · offline",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(onClick = { showCreate = !showCreate }) {
                    Text(if (showCreate) "Close form" else "Add entry")
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "The index merges manual entries with text-bearing Marginalia and Research anchors without duplicating their data.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (showCreate) {
                ManualIndexEntryForm(
                    documents = documents,
                    repository = indexRepository,
                    onSaved = { message ->
                        status = message
                        showCreate = false
                    },
                )
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Filter title, category or note") },
                singleLine = true,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TextButton(onClick = { selectedCategory = null }) {
                    Text(if (selectedCategory == null) "[All]" else "All")
                }
                categories.take(MAX_CATEGORY_BUTTONS).forEach { category ->
                    TextButton(onClick = { selectedCategory = category }) {
                        Text(if (selectedCategory.equals(category, ignoreCase = true)) "[$category]" else category)
                    }
                }
            }
            if (categories.size > MAX_CATEGORY_BUTTONS) {
                Text(
                    "Use the text filter for the remaining ${categories.size - MAX_CATEGORY_BUTTONS} categories.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            status?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }

            if (filtered.isEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        if (allItems.isEmpty()) {
                            "No index entries yet. Add one manually or pin Research/Marginalia anchors first."
                        } else {
                            "No entries match the current filter."
                        },
                        modifier = Modifier.padding(16.dp),
                    )
                }
            } else {
                grouped.forEach { (bucket, items) ->
                    Text(
                        bucket,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    items.forEach { item ->
                        val document = documentById[item.documentId]
                        IndexItemCard(
                            item = item,
                            document = document,
                            onGo = {
                                if (document != null && VaultDisplay.isPdf(document)) {
                                    onOpenPdf(document, item.pageIndex)
                                } else {
                                    status = "The source is indexed, but direct navigation is currently available for PDFs."
                                }
                            },
                            onDelete = if (item.source == UniversalIndexSource.MANUAL) {
                                {
                                    val rawId = item.id.removePrefix("manual:")
                                    status = "Removing manual index entry…"
                                    // The coroutine scope is owned by the helper card through this callback's caller.
                                    rawId
                                }
                            } else {
                                null
                            },
                            deleteManual = { id ->
                                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                                    indexRepository.delete(id)
                                    status = "Manual index entry removed."
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ManualIndexEntryForm(
    documents: List<VaultDocument>,
    repository: IndexRepository,
    onSaved: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("General") }
    var pageText by remember { mutableStateOf("1") }
    var startText by remember { mutableStateOf("") }
    var endText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var selectedDocumentId by remember { mutableStateOf<String?>(documents.firstOrNull()?.id) }

    LaunchedEffect(documents.map { it.id }) {
        if (selectedDocumentId == null || documents.none { it.id == selectedDocumentId }) {
            selectedDocumentId = documents.firstOrNull()?.id
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Create manual index entry", fontWeight = FontWeight.Bold)
            if (documents.isEmpty()) {
                Text("Import a document in Documents first.", color = MaterialTheme.colorScheme.error)
                return@Column
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Entry title") },
                singleLine = true,
            )
            OutlinedTextField(
                value = category,
                onValueChange = { category = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Category · e.g. Person, Place, Topic") },
                singleLine = true,
            )

            Text("Source document", style = MaterialTheme.typography.labelLarge)
            documents.take(MAX_DOCUMENT_PICKER_ROWS).forEach { document ->
                val selected = selectedDocumentId == document.id
                TextButton(
                    onClick = { selectedDocumentId = document.id },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (selected) "✓ ${document.displayName}" else document.displayName)
                }
            }
            if (documents.size > MAX_DOCUMENT_PICKER_ROWS) {
                Text(
                    "Showing the first $MAX_DOCUMENT_PICKER_ROWS documents. Narrowing large vaults is planned for the next index iteration.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = pageText,
                    onValueChange = { pageText = it.filter(Char::isDigit).take(6) },
                    modifier = Modifier.weight(1f),
                    label = { Text("Page") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = startText,
                    onValueChange = { startText = it.filter(Char::isDigit).take(9) },
                    modifier = Modifier.weight(1f),
                    label = { Text("Start offset · optional") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = endText,
                    onValueChange = { endText = it.filter(Char::isDigit).take(9) },
                    modifier = Modifier.weight(1f),
                    label = { Text("End offset · optional") },
                    singleLine = true,
                )
            }
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Note · optional") },
            )

            Button(
                onClick = {
                    val documentId = selectedDocumentId ?: return@Button
                    val now = System.currentTimeMillis()
                    scope.launch {
                        repository.save(
                            IndexEntry(
                                id = UUID.randomUUID().toString(),
                                title = title,
                                category = category,
                                documentId = documentId,
                                pageIndex = (pageText.toIntOrNull() ?: 1) - 1,
                                startOffset = startText.toIntOrNull(),
                                endOffsetExclusive = endText.toIntOrNull(),
                                note = note,
                                createdAtEpochMillis = now,
                                updatedAtEpochMillis = now,
                            ),
                        )
                        onSaved("Index entry saved locally.")
                    }
                },
                enabled = title.isNotBlank() && selectedDocumentId != null,
            ) {
                Text("Save index entry")
            }
        }
    }
}

@Composable
private fun IndexItemCard(
    item: UniversalIndexItem,
    document: VaultDocument?,
    onGo: () -> Unit,
    onDelete: (() -> String)?,
    deleteManual: (String) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(item.title, fontWeight = FontWeight.SemiBold)
            Text(
                "${item.category} · ${sourceLabel(item.source)} · page ${item.pageIndex + 1}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                document?.displayName ?: "Source document is no longer in the Vault",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            item.note?.takeIf { it != item.title }?.let { note ->
                Text(note.take(220), style = MaterialTheme.typography.bodySmall)
            }
            if (item.startOffset != null) {
                Text(
                    "Anchor ${item.startOffset}–${item.endOffsetExclusive ?: item.startOffset}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onGo, enabled = document != null) { Text("Go") }
                if (onDelete != null) {
                    TextButton(onClick = { deleteManual(onDelete()) }) { Text("Delete") }
                }
            }
        }
    }
}

private fun sourceLabel(source: UniversalIndexSource): String = when (source) {
    UniversalIndexSource.MANUAL -> "manual"
    UniversalIndexSource.RESEARCH -> "Research anchor"
    UniversalIndexSource.MARGINALIA -> "Marginalia"
}

private const val MAX_CATEGORY_BUTTONS = 6
private const val MAX_DOCUMENT_PICKER_ROWS = 24
