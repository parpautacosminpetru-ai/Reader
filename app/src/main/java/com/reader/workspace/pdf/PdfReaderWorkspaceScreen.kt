package com.reader.workspace.pdf

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import com.reader.workspace.marginalia.DocumentAnchor
import com.reader.workspace.marginalia.MarginaliaGeometry
import com.reader.workspace.marginalia.MarginaliaItem
import com.reader.workspace.marginalia.MarginaliaItemKind
import com.reader.workspace.marginalia.MarginaliaRepository
import com.reader.workspace.research.LexicalHit
import com.reader.workspace.research.LexicalMatchMode
import com.reader.workspace.research.LexicalSearchEngine
import com.reader.workspace.research.ProximityMatch
import com.reader.workspace.research.ProximityRule
import com.reader.workspace.research.ResearchAxisDefinition
import com.reader.workspace.research.ResearchHistoryEntry
import com.reader.workspace.research.ResearchPalette
import com.reader.workspace.research.ResearchProfile
import com.reader.workspace.research.ResearchRepository
import com.reader.workspace.storage.DocumentVaultRepository
import com.reader.workspace.storage.VaultDocument
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PdfReaderWorkspaceScreen(
    document: VaultDocument,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val vaultRepository = remember(context.applicationContext) {
        DocumentVaultRepository.get(context.applicationContext)
    }
    val researchRepository = remember(context.applicationContext) {
        ResearchRepository.get(context.applicationContext)
    }
    val file = remember(document.id) { vaultRepository.localFile(document) }
    val sessionResult = remember(file.absolutePath) { runCatching { PdfRendererSession(file) } }
    val session = sessionResult.getOrNull()

    DisposableEffect(session) {
        onDispose { session?.close() }
    }

    if (session == null) {
        WorkspaceOpenError(
            documentName = document.displayName,
            message = sessionResult.exceptionOrNull()?.message ?: "Unable to open PDF",
            onBack = onBack,
        )
        return
    }

    val savedAxes by researchRepository.axes.collectAsState(initial = emptyList())
    val savedProfiles by researchRepository.profiles.collectAsState(initial = emptyList())
    val history by remember(document.id) {
        researchRepository.history(document.id)
    }.collectAsState(initial = emptyList())

    var pageIndex by remember(document.id) { mutableStateOf(0) }
    var zoom by remember(document.id) { mutableFloatStateOf(1f) }
    var showMarginalia by remember(document.id) { mutableStateOf(false) }
    var showResearch by remember(document.id) { mutableStateOf(false) }
    var overlayEnabled by remember(document.id) { mutableStateOf(true) }
    var pageText by remember(document.id) { mutableStateOf<String?>(null) }
    var pageTextReady by remember(document.id) { mutableStateOf(false) }
    var selectedAxisIds by remember(document.id) { mutableStateOf<Set<String>>(emptySet()) }
    var selectedProfileId by remember(document.id) { mutableStateOf<String?>(null) }
    var proximityChars by remember(document.id) { mutableStateOf("300") }

    LaunchedEffect(session, pageIndex) {
        pageTextReady = false
        pageText = withContext(Dispatchers.IO) {
            runCatching { session.extractPageText(pageIndex) }.getOrNull()
        }
        pageTextReady = true
    }

    LaunchedEffect(savedAxes.map { it.id }) {
        val validIds = savedAxes.map { it.id }.toSet()
        selectedAxisIds = selectedAxisIds.intersect(validIds)
    }

    val activeAxes = remember(savedAxes, selectedAxisIds) {
        savedAxes.filter { it.id in selectedAxisIds && it.enabled }
    }
    val lexicalAxes = remember(activeAxes) { activeAxes.map(ResearchAxisDefinition::toLexicalAxis) }
    val sourceText = pageText.orEmpty()
    val hits = remember(sourceText, lexicalAxes) {
        LexicalSearchEngine.search(sourceText, lexicalAxes)
    }
    val proximity = remember(hits, activeAxes, proximityChars) {
        if (activeAxes.size < 2) {
            emptyList()
        } else {
            LexicalSearchEngine.findProximityMatches(
                hits = hits,
                rules = listOf(
                    ProximityRule(
                        id = "workspace-intersection",
                        requiredAxisIds = activeAxes.map { it.id }.toSet(),
                        maxSpanChars = proximityChars.toIntOrNull()?.coerceAtLeast(0) ?: 300,
                    ),
                ),
            )
        }
    }
    val overlayRequests = remember(activeAxes, hits, overlayEnabled) {
        if (!overlayEnabled) {
            emptyList()
        } else {
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
    }

    Scaffold(
        modifier = Modifier.safeDrawingPadding(),
        topBar = {
            WorkspaceHeader(
                documentName = document.displayName,
                pageIndex = pageIndex,
                pageCount = session.pageCount,
                zoom = zoom,
                showMarginalia = showMarginalia,
                showResearch = showResearch,
                overlayEnabled = overlayEnabled,
                activeAxisCount = activeAxes.size,
                onBack = onBack,
                onPrevious = { pageIndex = (pageIndex - 1).coerceAtLeast(0) },
                onNext = { pageIndex = (pageIndex + 1).coerceAtMost(session.pageCount - 1) },
                onZoomOut = { zoom = (zoom - 0.25f).coerceAtLeast(0.75f) },
                onZoomIn = { zoom = (zoom + 0.25f).coerceAtMost(3f) },
                onToggleMarginalia = { showMarginalia = !showMarginalia },
                onToggleResearch = { showResearch = !showResearch },
                onToggleOverlay = { overlayEnabled = !overlayEnabled },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (showResearch) {
                WorkspaceResearchPanel(
                    documentId = document.id,
                    pageIndex = pageIndex,
                    pageNumber = pageIndex + 1,
                    pageText = pageText,
                    pageTextReady = pageTextReady,
                    repository = researchRepository,
                    savedAxes = savedAxes,
                    savedProfiles = savedProfiles,
                    history = history,
                    selectedAxisIds = selectedAxisIds,
                    selectedProfileId = selectedProfileId,
                    proximityChars = proximityChars,
                    overlayEnabled = overlayEnabled,
                    hits = hits,
                    proximity = proximity,
                    onSelectedAxisIdsChange = {
                        selectedAxisIds = it
                        selectedProfileId = null
                    },
                    onSelectedProfileIdChange = { selectedProfileId = it },
                    onProximityCharsChange = { proximityChars = it },
                    onOverlayEnabledChange = { overlayEnabled = it },
                )
            }

            WorkspaceDocumentArea(
                modifier = Modifier.weight(1f),
                document = document,
                session = session,
                pageIndex = pageIndex,
                zoom = zoom,
                pageText = sourceText,
                showMarginalia = showMarginalia,
                overlayRequests = overlayRequests,
            )
        }
    }
}

@Composable
private fun WorkspaceHeader(
    documentName: String,
    pageIndex: Int,
    pageCount: Int,
    zoom: Float,
    showMarginalia: Boolean,
    showResearch: Boolean,
    overlayEnabled: Boolean,
    activeAxisCount: Int,
    onBack: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onZoomOut: () -> Unit,
    onZoomIn: () -> Unit,
    onToggleMarginalia: () -> Unit,
    onToggleResearch: () -> Unit,
    onToggleOverlay: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("Back") }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = documentName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Text(
                    text = "Page ${pageIndex + 1} / $pageCount · ${(zoom * 100).toInt()}% · $activeAxisCount active axis/axes",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onPrevious, enabled = pageIndex > 0) { Text("Prev") }
            TextButton(onClick = onNext, enabled = pageIndex + 1 < pageCount) { Text("Next") }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onZoomOut, enabled = zoom > 0.75f) { Text("− Zoom") }
            TextButton(onClick = onZoomIn, enabled = zoom < 3f) { Text("+ Zoom") }
            TextButton(onClick = onToggleMarginalia) {
                Text(if (showMarginalia) "Hide Marginalia" else "Marginalia")
            }
            TextButton(onClick = onToggleResearch) {
                Text(if (showResearch) "Hide Research" else "Research")
            }
            TextButton(
                onClick = onToggleOverlay,
                enabled = activeAxisCount > 0 && PdfResearchOverlay.isAvailable(),
            ) {
                Text(if (overlayEnabled) "Overlay ON" else "Overlay OFF")
            }
        }
    }
}

@Composable
private fun WorkspaceDocumentArea(
    modifier: Modifier,
    document: VaultDocument,
    session: PdfRendererSession,
    pageIndex: Int,
    zoom: Float,
    pageText: String,
    showMarginalia: Boolean,
    overlayRequests: List<PdfTextHighlightRequest>,
) {
    val context = LocalContext.current
    val marginaliaRepository = remember(context.applicationContext) {
        MarginaliaRepository.get(context.applicationContext)
    }
    val storedWidth by remember(document.id) {
        marginaliaRepository.observeWidth(document.id)
    }.collectAsState(initial = MarginaliaGeometry.DEFAULT_WIDTH_FRACTION)
    var marginaliaWidth by remember(document.id) {
        mutableFloatStateOf(MarginaliaGeometry.DEFAULT_WIDTH_FRACTION)
    }

    LaunchedEffect(storedWidth) {
        marginaliaWidth = storedWidth
    }

    Row(modifier = modifier.fillMaxSize()) {
        val pdfWeight = if (showMarginalia) (1f - marginaliaWidth).coerceAtLeast(0.2f) else 1f
        WorkspacePdfViewport(
            modifier = Modifier.weight(pdfWeight),
            session = session,
            pageIndex = pageIndex,
            zoom = zoom,
            overlayRequests = overlayRequests,
        )

        if (showMarginalia) {
            WorkspaceMarginaliaPanel(
                modifier = Modifier.weight(marginaliaWidth.coerceAtLeast(0.1f)),
                document = document,
                pageIndex = pageIndex,
                pageText = pageText,
                repository = marginaliaRepository,
                widthFraction = marginaliaWidth,
                onWidthFractionChange = {
                    marginaliaWidth = MarginaliaGeometry.clampWidthFraction(it)
                },
            )
        }
    }
}

@Composable
private fun WorkspacePdfViewport(
    modifier: Modifier,
    session: PdfRendererSession,
    pageIndex: Int,
    zoom: Float,
    overlayRequests: List<PdfTextHighlightRequest>,
) {
    val density = LocalDensity.current
    var bitmap by remember(session) { mutableStateOf<Bitmap?>(null) }
    var renderError by remember(session) { mutableStateOf<String?>(null) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        val baseWidthPx = with(density) { maxWidth.roundToPx() }.coerceAtLeast(1)
        val targetWidthPx = (baseWidthPx * zoom).toInt().coerceAtLeast(1)

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
                text = "Could not render this page: $renderError",
                modifier = Modifier.padding(20.dp),
                color = MaterialTheme.colorScheme.error,
            )
            bitmap == null -> Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text("Rendering page and research overlay locally…")
            }
            else -> {
                val horizontalScroll = rememberScrollState()
                val verticalScroll = rememberScrollState()
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .horizontalScroll(horizontalScroll)
                        .verticalScroll(verticalScroll)
                        .padding(8.dp),
                ) {
                    val imageWidth = with(density) { bitmap!!.width.toDp() }
                    Image(
                        bitmap = bitmap!!.asImageBitmap(),
                        contentDescription = "PDF page ${pageIndex + 1}",
                        modifier = Modifier.width(imageWidth),
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkspaceMarginaliaPanel(
    modifier: Modifier,
    document: VaultDocument,
    pageIndex: Int,
    pageText: String,
    repository: MarginaliaRepository,
    widthFraction: Float,
    onWidthFractionChange: (Float) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val allItems by remember(document.id) {
        repository.observeItems(document.id)
    }.collectAsState(initial = emptyList())
    var note by remember(document.id, pageIndex) { mutableStateOf("") }
    var status by remember(document.id) { mutableStateOf<String?>(null) }
    val anchor = remember(document.id, pageIndex) {
        DocumentAnchor(documentId = document.id, pageIndex = pageIndex)
    }
    val pageItems = remember(allItems, pageIndex) {
        allItems.filter { it.anchor.pageIndex == pageIndex }
    }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Marginalia", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            "Page ${pageIndex + 1} · persistent local layer",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text("Width ${(widthFraction * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
        Slider(
            value = widthFraction,
            onValueChange = onWidthFractionChange,
            onValueChangeFinished = {
                scope.launch {
                    repository.saveWidth(document.id, widthFraction)
                    status = "Margin width saved."
                }
            },
            valueRange = MarginaliaGeometry.MIN_WIDTH_FRACTION..MarginaliaGeometry.MAX_WIDTH_FRACTION,
        )
        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Page note") },
            minLines = 3,
        )
        Button(
            onClick = {
                val text = note.trim()
                if (text.isNotEmpty()) {
                    scope.launch {
                        repository.saveItem(
                            MarginaliaItem(
                                id = UUID.randomUUID().toString(),
                                anchor = anchor,
                                kind = MarginaliaItemKind.TEXT,
                                xFraction = 0.05f,
                                yFraction = 0.05f,
                                widthFraction = 0.9f,
                                heightFraction = 0.15f,
                                text = text,
                            ),
                        )
                        note = ""
                        status = "Marker saved locally."
                    }
                }
            },
            enabled = note.isNotBlank(),
        ) { Text("Save text marker") }

        status?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
        Text(
            "${pageItems.size} saved marker(s) · source PDF unchanged",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        pageItems.forEach { item ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(item.kind.name, style = MaterialTheme.typography.labelMedium)
                    Text(item.text.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { scope.launch { repository.deleteItem(item.id) } }) {
                        Text("Delete marker")
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkspaceResearchPanel(
    documentId: String,
    pageIndex: Int,
    pageNumber: Int,
    pageText: String?,
    pageTextReady: Boolean,
    repository: ResearchRepository,
    savedAxes: List<ResearchAxisDefinition>,
    savedProfiles: List<ResearchProfile>,
    history: List<ResearchHistoryEntry>,
    selectedAxisIds: Set<String>,
    selectedProfileId: String?,
    proximityChars: String,
    overlayEnabled: Boolean,
    hits: List<LexicalHit>,
    proximity: List<ProximityMatch>,
    onSelectedAxisIdsChange: (Set<String>) -> Unit,
    onSelectedProfileIdChange: (String?) -> Unit,
    onProximityCharsChange: (String) -> Unit,
    onOverlayEnabledChange: (Boolean) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var newAxisTitle by remember { mutableStateOf("") }
    var newAxisForms by remember { mutableStateOf("") }
    var newAxisMode by remember { mutableStateOf(LexicalMatchMode.PREFIX) }
    var profileTitle by remember { mutableStateOf("") }
    var status by remember(documentId) { mutableStateOf<String?>(null) }
    val activeAxes = remember(savedAxes, selectedAxisIds) {
        savedAxes.filter { it.id in selectedAxisIds && it.enabled }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 500.dp)
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Lexical Research · page $pageNumber", style = MaterialTheme.typography.titleMedium)
                TextButton(
                    onClick = { onOverlayEnabledChange(!overlayEnabled) },
                    enabled = activeAxes.isNotEmpty() && PdfResearchOverlay.isAvailable(),
                ) {
                    Text(if (overlayEnabled) "Hide colors" else "Show colors")
                }
            }

            Text(
                "${savedAxes.size} saved axis/axes · ${savedProfiles.size} profile(s) · deterministic/offline",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when {
                !pageTextReady -> {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text("Reading page text locally…", style = MaterialTheme.typography.bodySmall)
                }
                pageText == null -> Text(
                    "Native text overlay needs Android 15/API 35+; scanned pages still need the later OCR path.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                pageText.isEmpty() -> Text(
                    "No embedded text was found on this page. It may be scanned/image-only.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (hits.size > PdfResearchOverlay.MAX_HIGHLIGHTS_PER_PAGE) {
                Text(
                    "${hits.size} hits found; overlay draws the first ${PdfResearchOverlay.MAX_HIGHLIGHTS_PER_PAGE} on this page for responsiveness.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text("Create reusable axis", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = newAxisTitle,
                onValueChange = { newAxisTitle = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Axis title") },
                singleLine = true,
            )
            OutlinedTextField(
                value = newAxisForms,
                onValueChange = { newAxisForms = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Lexical forms · comma/newline separated") },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LexicalMatchMode.entries.forEach { mode ->
                    TextButton(onClick = { newAxisMode = mode }) {
                        Text(if (newAxisMode == mode) "[${workspaceModeLabel(mode)}]" else workspaceModeLabel(mode))
                    }
                }
            }
            Button(
                onClick = {
                    val title = newAxisTitle.trim()
                    val patterns = workspaceSplitPatterns(newAxisForms)
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
                            newAxisTitle = ""
                            newAxisForms = ""
                            status = "Axis saved, activated and color-coded."
                        }
                    }
                },
                enabled = newAxisTitle.isNotBlank() && workspaceSplitPatterns(newAxisForms).isNotEmpty(),
            ) { Text("Save axis") }

            if (savedAxes.isNotEmpty()) {
                Text("Saved axes / live legend", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                savedAxes.forEach { axis ->
                    val active = axis.id in selectedAxisIds
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .background(Color(axis.colorArgb.toInt()), RoundedCornerShape(3.dp)),
                                )
                                Text(
                                    "${if (active) "✓ " else ""}${axis.title}",
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                )
                            }
                            Text(
                                "${workspaceModeLabel(axis.matchMode)} · ${axis.patterns.joinToString(limit = 6, truncated = "…")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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
                                            status = "Axis deleted."
                                        }
                                    },
                                ) { Text("Delete") }
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = proximityChars,
                onValueChange = { onProximityCharsChange(it.filter(Char::isDigit).take(6)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Maximum intersection span (characters)") },
                singleLine = true,
            )
            Text(
                "${hits.size} hit(s) · ${proximity.size} dense intersection(s)",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            WorkspaceResearchResults(hits, proximity)

            if (activeAxes.isNotEmpty()) {
                OutlinedTextField(
                    value = profileTitle,
                    onValueChange = { profileTitle = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Profile title") },
                    singleLine = true,
                )
                Button(
                    onClick = {
                        val title = profileTitle.trim()
                        if (title.isNotEmpty()) {
                            val now = System.currentTimeMillis()
                            val orderedIds = savedAxes.filter { it.id in selectedAxisIds }.map { it.id }
                            val id = UUID.randomUUID().toString()
                            scope.launch {
                                repository.saveProfile(
                                    ResearchProfile(
                                        id = id,
                                        title = title,
                                        axisIds = orderedIds,
                                        proximityChars = proximityChars.toIntOrNull()?.coerceAtLeast(0) ?: 300,
                                        createdAtEpochMillis = now,
                                        updatedAtEpochMillis = now,
                                    ),
                                )
                                onSelectedProfileIdChange(id)
                                profileTitle = ""
                                status = "Research profile saved."
                            }
                        }
                    },
                    enabled = profileTitle.isNotBlank(),
                ) { Text("Save active axes as profile") }
            }

            savedProfiles.forEach { profile ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(if (selectedProfileId == profile.id) "✓ ${profile.title}" else profile.title)
                        Text(
                            "${profile.axisIds.size} axis/axes · span ${profile.proximityChars}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(
                        onClick = {
                            val validIds = savedAxes.map { it.id }.toSet()
                            onSelectedAxisIdsChange(profile.axisIds.filter { it in validIds }.toSet())
                            onProximityCharsChange(profile.proximityChars.toString())
                            onSelectedProfileIdChange(profile.id)
                            status = "Profile loaded."
                        },
                    ) { Text("Use") }
                    TextButton(onClick = { scope.launch { repository.deleteProfile(profile.id) } }) {
                        Text("Delete")
                    }
                }
            }

            Button(
                onClick = {
                    val now = System.currentTimeMillis()
                    scope.launch {
                        repository.recordHistory(
                            ResearchHistoryEntry(
                                id = UUID.randomUUID().toString(),
                                documentId = documentId,
                                pageIndex = pageIndex,
                                profileId = selectedProfileId,
                                axisIds = activeAxes.map { it.id },
                                proximityChars = proximityChars.toIntOrNull()?.coerceAtLeast(0) ?: 300,
                                hitCount = hits.size,
                                intersectionCount = proximity.size,
                                executedAtEpochMillis = now,
                            ),
                        )
                        status = "Search saved to local history."
                    }
                },
                enabled = activeAxes.isNotEmpty() && pageTextReady && pageText != null,
            ) { Text("Run & save history") }

            status?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            if (history.isNotEmpty()) {
                Text("Recent searches in this document", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                history.take(5).forEach { entry ->
                    Text(
                        "page ${entry.pageIndex + 1} · ${entry.axisIds.size} axis/axes · ${entry.hitCount} hits · ${entry.intersectionCount} intersections",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkspaceResearchResults(
    hits: List<LexicalHit>,
    proximity: List<ProximityMatch>,
) {
    hits.take(10).forEach { hit ->
        Text(
            "${hit.axisTitle}: “${hit.matchedText}” @ ${hit.startOffset}",
            style = MaterialTheme.typography.bodySmall,
        )
    }
    proximity.take(6).forEach { match ->
        Text(
            "intersection ${match.startOffset}–${match.endOffsetExclusive} · " +
                match.hits.map { it.axisTitle }.distinct().joinToString(" + "),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private fun workspaceSplitPatterns(raw: String): List<String> = raw
    .split(',', ';', '\n')
    .map(String::trim)
    .filter(String::isNotEmpty)
    .distinct()

private fun workspaceModeLabel(mode: LexicalMatchMode): String = when (mode) {
    LexicalMatchMode.EXACT -> "Exact"
    LexicalMatchMode.PREFIX -> "Stem"
    LexicalMatchMode.CONTAINS -> "Contains"
}

@Composable
private fun WorkspaceOpenError(
    documentName: String,
    message: String,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Could not open $documentName", style = MaterialTheme.typography.titleLarge)
        Text(message, color = MaterialTheme.colorScheme.error)
        Button(onClick = onBack) { Text("Back to Documents") }
    }
}
