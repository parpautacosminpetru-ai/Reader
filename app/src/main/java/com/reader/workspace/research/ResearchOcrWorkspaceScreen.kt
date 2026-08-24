package com.reader.workspace.research

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.reader.workspace.pdf.DocumentResearchCartographyPanel
import com.reader.workspace.pdf.PdfPageTextResolver
import com.reader.workspace.pdf.PdfPageTextResult
import com.reader.workspace.pdf.PdfPageTextSource
import com.reader.workspace.pdf.PdfRendererSession
import com.reader.workspace.pdf.PdfTextHighlightRequest
import com.reader.workspace.storage.DocumentVaultRepository
import com.reader.workspace.storage.VaultDocument
import com.reader.workspace.storage.VaultFileNames
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ResearchOcrWorkspaceScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val vaultRepository = remember(context.applicationContext) {
        DocumentVaultRepository.get(context.applicationContext)
    }
    val researchRepository = remember(context.applicationContext) {
        ResearchRepository.get(context.applicationContext)
    }
    val documents by vaultRepository.documents.collectAsState(initial = emptyList())
    val savedAxes by researchRepository.axes.collectAsState(initial = emptyList())
    val savedProfiles by researchRepository.profiles.collectAsState(initial = emptyList())
    var selectedDocument by remember { mutableStateOf<VaultDocument?>(null) }

    if (selectedDocument == null) {
        OcrResearchDocumentPicker(
            documents = documents.filter(VaultDocument::isPdfForOcrResearch),
            savedAxisCount = savedAxes.size,
            savedProfileCount = savedProfiles.size,
            onBack = onBack,
            onSelect = { selectedDocument = it },
        )
    } else {
        OcrResearchDocumentWorkspace(
            document = selectedDocument!!,
            vaultRepository = vaultRepository,
            repository = researchRepository,
            savedAxes = savedAxes,
            savedProfiles = savedProfiles,
            onBack = { selectedDocument = null },
        )
    }
}

@Composable
private fun OcrResearchDocumentPicker(
    documents: List<VaultDocument>,
    savedAxisCount: Int,
    savedProfileCount: Int,
    onBack: () -> Unit,
    onSelect: (VaultDocument) -> Unit,
) {
    Scaffold(
        modifier = Modifier.safeDrawingPadding(),
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack) { Text("Back") }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Lexical Research", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "$savedAxisCount axes · $savedProfileCount profiles · bundled offline OCR",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "Choose a PDF. Reader uses its embedded text when available and automatically falls back to the bundled Latin OCR model for scanned pages.",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (documents.isEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text("No PDFs in the local Vault yet.", modifier = Modifier.padding(16.dp))
                }
            } else {
                documents.forEach { document ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(document) },
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(document.displayName, fontWeight = FontWeight.Medium)
                            Text(
                                "Local PDF · native text + OCR fallback",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OcrResearchDocumentWorkspace(
    document: VaultDocument,
    vaultRepository: DocumentVaultRepository,
    repository: ResearchRepository,
    savedAxes: List<ResearchAxisDefinition>,
    savedProfiles: List<ResearchProfile>,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val file = remember(document.id) { vaultRepository.localFile(document) }
    val sessionResult = remember(file.absolutePath) { runCatching { PdfRendererSession(file) } }
    val session = sessionResult.getOrNull()

    if (session == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Could not open ${document.displayName}", style = MaterialTheme.typography.titleLarge)
            Text(
                sessionResult.exceptionOrNull()?.message ?: "Unknown PDF error",
                color = MaterialTheme.colorScheme.error,
            )
            Button(onClick = onBack) { Text("Back") }
        }
        return
    }

    val textResolver = remember(session) { PdfPageTextResolver(session) }
    DisposableEffect(session, textResolver) {
        onDispose {
            textResolver.close()
            session.close()
        }
    }

    val history by remember(document.id) {
        repository.history(document.id)
    }.collectAsState(initial = emptyList())

    var selectedAxisIds by remember(document.id) { mutableStateOf<Set<String>>(emptySet()) }
    var selectedProfileId by remember(document.id) { mutableStateOf<String?>(null) }
    var proximityText by remember(document.id) { mutableStateOf("300") }
    var pageIndex by remember(document.id) { mutableStateOf(0) }
    var pageText by remember(document.id) { mutableStateOf<PdfPageTextResult?>(null) }
    var pageTextReady by remember(document.id) { mutableStateOf(false) }
    var newAxisTitle by remember { mutableStateOf("") }
    var newAxisForms by remember { mutableStateOf("") }
    var newAxisMode by remember { mutableStateOf(LexicalMatchMode.PREFIX) }
    var profileTitle by remember { mutableStateOf("") }
    var status by remember(document.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(savedAxes.map { it.id }) {
        selectedAxisIds = selectedAxisIds.intersect(savedAxes.map { it.id }.toSet())
    }

    LaunchedEffect(textResolver, pageIndex) {
        pageTextReady = false
        pageText = runCatching { textResolver.resolve(pageIndex) }.getOrElse {
            PdfPageTextResult("", PdfPageTextSource.NONE)
        }
        pageTextReady = true
    }

    val activeAxes = remember(savedAxes, selectedAxisIds) {
        savedAxes.filter { it.id in selectedAxisIds && it.enabled }
    }
    val lexicalAxes = remember(activeAxes) { activeAxes.map(ResearchAxisDefinition::toLexicalAxis) }
    val hits = remember(pageText, lexicalAxes) {
        LexicalSearchEngine.search(pageText?.text.orEmpty(), lexicalAxes)
    }
    val overlayRequests = remember(activeAxes, hits) {
        val colors = activeAxes.associate { it.id to it.colorArgb }
        hits.mapNotNull { hit ->
            colors[hit.axisId]?.let { color ->
                PdfTextHighlightRequest(
                    axisId = hit.axisId,
                    startOffset = hit.startOffset,
                    endOffsetExclusive = hit.endOffsetExclusive,
                    colorArgb = color,
                )
            }
        }
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
                TextButton(onClick = onBack) { Text("PDFs") }
                Column(modifier = Modifier.weight(1f)) {
                    Text(document.displayName, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(
                        "Research 0.8 · page ${pageIndex + 1}/${session.pageCount} · ${activeAxes.size} active axis/axes",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(
                    onClick = { pageIndex = (pageIndex - 1).coerceAtLeast(0) },
                    enabled = pageIndex > 0,
                ) { Text("Prev") }
                TextButton(
                    onClick = { pageIndex = (pageIndex + 1).coerceAtMost(session.pageCount - 1) },
                    enabled = pageIndex + 1 < session.pageCount,
                ) { Text("Next") }
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
            OcrResearchAxesCard(
                repository = repository,
                savedAxes = savedAxes,
                savedProfiles = savedProfiles,
                selectedAxisIds = selectedAxisIds,
                selectedProfileId = selectedProfileId,
                proximityText = proximityText,
                newAxisTitle = newAxisTitle,
                newAxisForms = newAxisForms,
                newAxisMode = newAxisMode,
                profileTitle = profileTitle,
                onSelectedAxisIdsChange = {
                    selectedAxisIds = it
                    selectedProfileId = null
                },
                onSelectedProfileIdChange = { selectedProfileId = it },
                onProximityTextChange = { proximityText = it },
                onNewAxisTitleChange = { newAxisTitle = it },
                onNewAxisFormsChange = { newAxisForms = it },
                onNewAxisModeChange = { newAxisMode = it },
                onProfileTitleChange = { profileTitle = it },
                onStatus = { status = it },
            )

            DocumentResearchCartographyPanel(
                documentId = document.id,
                session = session,
                textResolver = textResolver,
                pageCount = session.pageCount,
                activeAxes = activeAxes,
                selectedProfileId = selectedProfileId,
                proximityChars = proximityText.toIntOrNull()?.coerceAtLeast(0) ?: 300,
                repository = repository,
                onNavigateToPage = { target ->
                    pageIndex = target.coerceIn(0, session.pageCount - 1)
                    status = "Preview moved to page ${pageIndex + 1}."
                },
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("Page preview", fontWeight = FontWeight.Bold)
                    when {
                        !pageTextReady -> {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Text("Reading page locally…", style = MaterialTheme.typography.bodySmall)
                        }
                        pageText == null -> Text("Text unavailable", color = MaterialTheme.colorScheme.error)
                        else -> {
                            val source = when (pageText!!.source) {
                                PdfPageTextSource.NATIVE -> "embedded PDF text"
                                PdfPageTextSource.OCR -> "offline OCR"
                                PdfPageTextSource.NONE -> "no text detected"
                            }
                            Text(
                                "$source · ${hits.size} hit(s) on page ${pageIndex + 1}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }

                    OcrAwareResearchPagePreview(
                        session = session,
                        textResolver = textResolver,
                        pageIndex = pageIndex,
                        pageText = pageText,
                        overlayRequests = overlayRequests,
                    )
                }
            }

            status?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }

            if (history.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Recent research history", fontWeight = FontWeight.Bold)
                        history.take(8).forEach { entry ->
                            Text(
                                "${entry.scopeLabel()} · ${entry.hitCount} hits · ${entry.intersectionCount} intersections",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OcrResearchAxesCard(
    repository: ResearchRepository,
    savedAxes: List<ResearchAxisDefinition>,
    savedProfiles: List<ResearchProfile>,
    selectedAxisIds: Set<String>,
    selectedProfileId: String?,
    proximityText: String,
    newAxisTitle: String,
    newAxisForms: String,
    newAxisMode: LexicalMatchMode,
    profileTitle: String,
    onSelectedAxisIdsChange: (Set<String>) -> Unit,
    onSelectedProfileIdChange: (String?) -> Unit,
    onProximityTextChange: (String) -> Unit,
    onNewAxisTitleChange: (String) -> Unit,
    onNewAxisFormsChange: (String) -> Unit,
    onNewAxisModeChange: (LexicalMatchMode) -> Unit,
    onProfileTitleChange: (String) -> Unit,
    onStatus: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val activeAxes = savedAxes.filter { it.id in selectedAxisIds && it.enabled }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Axes and profiles", fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = newAxisTitle,
                onValueChange = onNewAxisTitleChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("New axis title") },
                singleLine = true,
            )
            OutlinedTextField(
                value = newAxisForms,
                onValueChange = onNewAxisFormsChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Lexical forms · comma/newline separated") },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                LexicalMatchMode.entries.forEach { mode ->
                    TextButton(onClick = { onNewAxisModeChange(mode) }) {
                        Text(if (newAxisMode == mode) "[${ocrModeLabel(mode)}]" else ocrModeLabel(mode))
                    }
                }
            }
            Button(
                onClick = {
                    val title = newAxisTitle.trim()
                    val patterns = ocrSplitPatterns(newAxisForms)
                    if (title.isNotEmpty() && patterns.isNotEmpty()) {
                        val now = System.currentTimeMillis()
                        val id = UUID.randomUUID().toString()
                        scope.launch {
                            repository.saveAxis(
                                ResearchAxisDefinition(
                                    id = id,
                                    title = title,
                                    patterns = patterns,
                                    matchMode = newAxisMode,
                                    colorArgb = ResearchPalette.colorFor(savedAxes.size),
                                    createdAtEpochMillis = now,
                                    updatedAtEpochMillis = now,
                                ),
                            )
                            onSelectedAxisIdsChange(selectedAxisIds + id)
                            onNewAxisTitleChange("")
                            onNewAxisFormsChange("")
                            onStatus("Axis saved and activated.")
                        }
                    }
                },
                enabled = newAxisTitle.isNotBlank() && ocrSplitPatterns(newAxisForms).isNotEmpty(),
            ) { Text("Save axis") }

            savedAxes.forEach { axis ->
                val active = axis.id in selectedAxisIds
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .background(Color(axis.colorArgb.toInt()), RoundedCornerShape(3.dp)),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${if (active) "✓ " else ""}${axis.title}")
                        Text(
                            "${ocrModeLabel(axis.matchMode)} · ${axis.patterns.joinToString(limit = 5, truncated = "…")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(
                        onClick = {
                            onSelectedAxisIdsChange(
                                if (active) selectedAxisIds - axis.id else selectedAxisIds + axis.id,
                            )
                        },
                    ) { Text(if (active) "Disable" else "Activate") }
                    TextButton(
                        onClick = {
                            scope.launch {
                                repository.deleteAxis(axis.id)
                                onSelectedAxisIdsChange(selectedAxisIds - axis.id)
                            }
                        },
                    ) { Text("Delete") }
                }
            }

            OutlinedTextField(
                value = proximityText,
                onValueChange = { onProximityTextChange(it.filter(Char::isDigit).take(6)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Intersection span in characters") },
                singleLine = true,
            )

            if (activeAxes.isNotEmpty()) {
                OutlinedTextField(
                    value = profileTitle,
                    onValueChange = onProfileTitleChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Profile title") },
                    singleLine = true,
                )
                Button(
                    onClick = {
                        val title = profileTitle.trim()
                        if (title.isNotEmpty()) {
                            val now = System.currentTimeMillis()
                            val id = UUID.randomUUID().toString()
                            scope.launch {
                                repository.saveProfile(
                                    ResearchProfile(
                                        id = id,
                                        title = title,
                                        axisIds = savedAxes.filter { it.id in selectedAxisIds }.map { it.id },
                                        proximityChars = proximityText.toIntOrNull()?.coerceAtLeast(0) ?: 300,
                                        createdAtEpochMillis = now,
                                        updatedAtEpochMillis = now,
                                    ),
                                )
                                onSelectedProfileIdChange(id)
                                onProfileTitleChange("")
                                onStatus("Profile saved.")
                            }
                        }
                    },
                    enabled = profileTitle.isNotBlank(),
                ) { Text("Save profile") }
            }

            savedProfiles.forEach { profile ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (selectedProfileId == profile.id) "✓ ${profile.title}" else profile.title,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = {
                            val validIds = savedAxes.map { it.id }.toSet()
                            onSelectedAxisIdsChange(profile.axisIds.filter { it in validIds }.toSet())
                            onSelectedProfileIdChange(profile.id)
                            onProximityTextChange(profile.proximityChars.toString())
                            onStatus("Profile loaded.")
                        },
                    ) { Text("Use") }
                    TextButton(onClick = { scope.launch { repository.deleteProfile(profile.id) } }) {
                        Text("Delete")
                    }
                }
            }
        }
    }
}

@Composable
private fun OcrAwareResearchPagePreview(
    session: PdfRendererSession,
    textResolver: PdfPageTextResolver,
    pageIndex: Int,
    pageText: PdfPageTextResult?,
    overlayRequests: List<PdfTextHighlightRequest>,
) {
    val density = LocalDensity.current
    var bitmap by remember(session) { mutableStateOf<Bitmap?>(null) }
    var renderError by remember(session) { mutableStateOf<String?>(null) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        val targetWidthPx = with(density) { maxWidth.roundToPx() }.coerceAtLeast(1)
        LaunchedEffect(session, textResolver, pageIndex, targetWidthPx, pageText, overlayRequests) {
            bitmap = null
            renderError = null
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val resolved = if (pageText == null) {
                        emptyList()
                    } else {
                        textResolver.resolveHighlights(
                            pageIndex = pageIndex,
                            pageText = pageText,
                            requests = overlayRequests,
                        )
                    }
                    session.renderPage(pageIndex, targetWidthPx, resolved)
                }
            }
            bitmap = result.getOrNull()
            renderError = result.exceptionOrNull()?.message
        }

        when {
            renderError != null -> Text(
                "Preview failed: $renderError",
                modifier = Modifier.padding(12.dp),
                color = MaterialTheme.colorScheme.error,
            )
            bitmap == null -> Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text("Rendering local preview…", style = MaterialTheme.typography.bodySmall)
            }
            else -> {
                val imageWidth = with(density) { bitmap!!.width.toDp() }
                Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = "OCR-aware research preview page ${pageIndex + 1}",
                    modifier = Modifier.width(imageWidth),
                )
            }
        }
    }
}

private fun VaultDocument.isPdfForOcrResearch(): Boolean =
    mimeType.equals("application/pdf", ignoreCase = true) ||
        VaultFileNames.extensionFrom(displayName) == "pdf"

private fun ocrSplitPatterns(raw: String): List<String> = raw
    .split(',', ';', '\n')
    .map(String::trim)
    .filter(String::isNotEmpty)
    .distinct()

private fun ocrModeLabel(mode: LexicalMatchMode): String = when (mode) {
    LexicalMatchMode.EXACT -> "Exact"
    LexicalMatchMode.PREFIX -> "Stem"
    LexicalMatchMode.CONTAINS -> "Contains"
}
