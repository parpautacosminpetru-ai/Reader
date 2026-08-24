package com.reader.workspace.research

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import com.reader.workspace.pdf.PdfRendererSession
import com.reader.workspace.pdf.PdfResearchOverlay
import com.reader.workspace.pdf.PdfTextHighlightRequest
import com.reader.workspace.storage.DocumentVaultRepository
import com.reader.workspace.storage.VaultDocument
import com.reader.workspace.storage.VaultFileNames
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ResearchCartographyScreen(onBack: () -> Unit) {
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
        ResearchDocumentPicker(
            documents = documents.filter(VaultDocument::isPdfForCartography),
            savedAxisCount = savedAxes.size,
            savedProfileCount = savedProfiles.size,
            onBack = onBack,
            onSelect = { selectedDocument = it },
        )
    } else {
        ResearchDocumentWorkspace(
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
private fun ResearchDocumentPicker(
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
                    Text(
                        text = "Lexical Research",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "$savedAxisCount saved axis/axes · $savedProfileCount profile(s) · offline",
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
                text = "Choose a PDF from the local Vault to scan a page range or the whole source.",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (documents.isEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "No PDFs are stored in Reader yet. Import one from Documents first.",
                        modifier = Modifier.padding(16.dp),
                    )
                }
            } else {
                documents.forEach { document ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(document) },
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = document.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                text = "PDF · local/offline · open cartography",
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
private fun ResearchDocumentWorkspace(
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

    DisposableEffect(session) {
        onDispose { session?.close() }
    }

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
                text = sessionResult.exceptionOrNull()?.message ?: "Unknown PDF error",
                color = MaterialTheme.colorScheme.error,
            )
            Button(onClick = onBack) { Text("Back") }
        }
        return
    }

    val history by remember(document.id) {
        repository.history(document.id)
    }.collectAsState(initial = emptyList())
    var selectedAxisIds by remember(document.id) { mutableStateOf<Set<String>>(emptySet()) }
    var selectedProfileId by remember(document.id) { mutableStateOf<String?>(null) }
    var proximityText by remember(document.id) { mutableStateOf("300") }
    var pageIndex by remember(document.id) { mutableStateOf(0) }
    var pageText by remember(document.id) { mutableStateOf<String?>(null) }
    var pageTextReady by remember(document.id) { mutableStateOf(false) }
    var newAxisTitle by remember { mutableStateOf("") }
    var newAxisForms by remember { mutableStateOf("") }
    var newAxisMode by remember { mutableStateOf(LexicalMatchMode.PREFIX) }
    var profileTitle by remember { mutableStateOf("") }
    var status by remember(document.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(savedAxes.map { it.id }) {
        selectedAxisIds = selectedAxisIds.intersect(savedAxes.map { it.id }.toSet())
    }

    LaunchedEffect(session, pageIndex) {
        pageTextReady = false
        pageText = withContext(Dispatchers.IO) {
            runCatching { session.extractPageText(pageIndex) }.getOrNull()
        }
        pageTextReady = true
    }

    val activeAxes = remember(savedAxes, selectedAxisIds) {
        savedAxes.filter { it.id in selectedAxisIds && it.enabled }
    }
    val lexicalAxes = remember(activeAxes) {
        activeAxes.map(ResearchAxisDefinition::toLexicalAxis)
    }
    val hits = remember(pageText, lexicalAxes) {
        LexicalSearchEngine.search(pageText.orEmpty(), lexicalAxes)
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
                    Text(
                        text = document.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                    Text(
                        text = "Cartography · page ${pageIndex + 1}/${session.pageCount} · ${activeAxes.size} active axis/axes",
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
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Axes and profiles", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = newAxisTitle,
                        onValueChange = { newAxisTitle = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("New axis title") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = newAxisForms,
                        onValueChange = { newAxisForms = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Lexical forms · comma/newline separated") },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        LexicalMatchMode.entries.forEach { mode ->
                            TextButton(onClick = { newAxisMode = mode }) {
                                Text(if (newAxisMode == mode) "[${cartographyModeLabel(mode)}]" else cartographyModeLabel(mode))
                            }
                        }
                    }
                    Button(
                        onClick = {
                            val title = newAxisTitle.trim()
                            val forms = cartographySplitPatterns(newAxisForms)
                            if (title.isNotEmpty() && forms.isNotEmpty()) {
                                val now = System.currentTimeMillis()
                                val id = UUID.randomUUID().toString()
                                scope.launch {
                                    repository.saveAxis(
                                        ResearchAxisDefinition(
                                            id = id,
                                            title = title,
                                            patterns = forms,
                                            matchMode = newAxisMode,
                                            colorArgb = ResearchPalette.colorFor(savedAxes.size),
                                            createdAtEpochMillis = now,
                                            updatedAtEpochMillis = now,
                                        ),
                                    )
                                    selectedAxisIds = selectedAxisIds + id
                                    newAxisTitle = ""
                                    newAxisForms = ""
                                    status = "Axis saved and activated."
                                }
                            }
                        },
                        enabled = newAxisTitle.isNotBlank() && cartographySplitPatterns(newAxisForms).isNotEmpty(),
                    ) { Text("Save axis") }

                    savedAxes.forEach { axis ->
                        val active = axis.id in selectedAxisIds
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                BoxWithConstraints(
                                    modifier = Modifier
                                        .size(14.dp)
                                        .background(Color(axis.colorArgb.toInt()), RoundedCornerShape(3.dp)),
                                ) {}
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${if (active) "✓ " else ""}${axis.title}",
                                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                    )
                                    Text(
                                        text = "${cartographyModeLabel(axis.matchMode)} · ${axis.patterns.joinToString(limit = 5, truncated = "…")}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                TextButton(
                                    onClick = {
                                        selectedAxisIds = if (active) selectedAxisIds - axis.id else selectedAxisIds + axis.id
                                        selectedProfileId = null
                                    },
                                ) { Text(if (active) "Disable" else "Activate") }
                                TextButton(
                                    onClick = {
                                        scope.launch {
                                            repository.deleteAxis(axis.id)
                                            selectedAxisIds = selectedAxisIds - axis.id
                                        }
                                    },
                                ) { Text("Delete") }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = proximityText,
                        onValueChange = { proximityText = it.filter(Char::isDigit).take(6) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Intersection span in characters") },
                        singleLine = true,
                    )

                    if (activeAxes.isNotEmpty()) {
                        OutlinedTextField(
                            value = profileTitle,
                            onValueChange = { profileTitle = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Save active axes as profile") },
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
                                        selectedProfileId = id
                                        profileTitle = ""
                                        status = "Profile saved."
                                    }
                                }
                            },
                            enabled = profileTitle.isNotBlank(),
                        ) { Text("Save profile") }
                    }

                    savedProfiles.forEach { profile ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = if (selectedProfileId == profile.id) "✓ ${profile.title}" else profile.title,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(
                                onClick = {
                                    val validIds = savedAxes.map { it.id }.toSet()
                                    selectedAxisIds = profile.axisIds.filter { it in validIds }.toSet()
                                    selectedProfileId = profile.id
                                    proximityText = profile.proximityChars.toString()
                                    status = "Profile loaded."
                                },
                            ) { Text("Use") }
                            TextButton(onClick = { scope.launch { repository.deleteProfile(profile.id) } }) {
                                Text("Delete")
                            }
                        }
                    }
                    status?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            DocumentResearchCartographyPanel(
                documentId = document.id,
                session = session,
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
                    Text(
                        text = "Page preview with active overlays",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    when {
                        !pageTextReady -> {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Text("Reading page ${pageIndex + 1}…", style = MaterialTheme.typography.bodySmall)
                        }
                        pageText == null -> Text(
                            "Native page text is unavailable on this Android version.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        else -> Text(
                            "${hits.size} hit(s) on this page · overlays ${if (PdfResearchOverlay.isAvailable()) "available" else "unavailable"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    ResearchPagePreview(
                        session = session,
                        pageIndex = pageIndex,
                        overlayRequests = overlayRequests,
                    )
                }
            }

            if (history.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text("Recent research history", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        history.take(8).forEach { entry ->
                            Text(
                                text = "${entry.scopeLabel()} · ${entry.hitCount} hits · ${entry.intersectionCount} intersections · ${entry.axisIds.size} axis/axes",
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
private fun ResearchPagePreview(
    session: PdfRendererSession,
    pageIndex: Int,
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
        LaunchedEffect(session, pageIndex, targetWidthPx, overlayRequests) {
            bitmap = null
            renderError = null
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val resolved = session.resolveTextHighlights(pageIndex, overlayRequests)
                    session.renderPage(pageIndex, targetWidthPx, resolved)
                }
            }
            bitmap = result.getOrNull()
            renderError = result.exceptionOrNull()?.message
        }

        when {
            renderError != null -> Text(
                text = "Preview failed: $renderError",
                modifier = Modifier.padding(12.dp),
                color = MaterialTheme.colorScheme.error,
            )
            bitmap == null -> Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text("Rendering preview locally…", style = MaterialTheme.typography.bodySmall)
            }
            else -> {
                val imageWidth = with(density) { bitmap!!.width.toDp() }
                Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = "Research preview page ${pageIndex + 1}",
                    modifier = Modifier.width(imageWidth),
                )
            }
        }
    }
}

private fun VaultDocument.isPdfForCartography(): Boolean =
    mimeType.equals("application/pdf", ignoreCase = true) ||
        VaultFileNames.extensionFrom(displayName) == "pdf"

private fun cartographySplitPatterns(raw: String): List<String> = raw
    .split(',', ';', '\n')
    .map(String::trim)
    .filter(String::isNotEmpty)
    .distinct()

private fun cartographyModeLabel(mode: LexicalMatchMode): String = when (mode) {
    LexicalMatchMode.EXACT -> "Exact"
    LexicalMatchMode.PREFIX -> "Stem"
    LexicalMatchMode.CONTAINS -> "Contains"
}
