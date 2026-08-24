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
import com.reader.workspace.marginalia.MarginaliaItem
import com.reader.workspace.marginalia.MarginaliaItemKind
import com.reader.workspace.marginalia.MarginaliaRepository
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
fun ResearchMarginaliaLinksScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val vaultRepository = remember(context.applicationContext) {
        DocumentVaultRepository.get(context.applicationContext)
    }
    val researchRepository = remember(context.applicationContext) {
        ResearchRepository.get(context.applicationContext)
    }
    val marginaliaRepository = remember(context.applicationContext) {
        MarginaliaRepository.get(context.applicationContext)
    }
    val documents by vaultRepository.documents.collectAsState(initial = emptyList())
    val axes by researchRepository.axes.collectAsState(initial = emptyList())
    val profiles by researchRepository.profiles.collectAsState(initial = emptyList())
    var selectedDocument by remember { mutableStateOf<VaultDocument?>(null) }

    if (selectedDocument == null) {
        ResearchLinkDocumentPicker(
            documents = documents.filter(VaultDocument::isPdfForResearchLinks),
            anchorCountLabel = "${axes.size} saved axes · ${profiles.size} profiles",
            onBack = onBack,
            onSelect = { selectedDocument = it },
        )
    } else {
        ResearchLinkWorkspace(
            document = selectedDocument!!,
            vaultRepository = vaultRepository,
            marginaliaRepository = marginaliaRepository,
            savedAxes = axes,
            savedProfiles = profiles,
            onBack = { selectedDocument = null },
        )
    }
}

@Composable
private fun ResearchLinkDocumentPicker(
    documents: List<VaultDocument>,
    anchorCountLabel: String,
    onBack: () -> Unit,
    onSelect: (VaultDocument) -> Unit,
) {
    Scaffold(
        modifier = Modifier.safeDrawingPadding(),
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack) { Text("Back") }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Research ↔ Marginalia", fontWeight = FontWeight.Bold)
                    Text(
                        anchorCountLabel,
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Choose a PDF from the local Vault. Source bytes are never changed.")
            if (documents.isEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text("No PDFs in Reader yet. Import one from Documents first.", modifier = Modifier.padding(16.dp))
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
                                "Open persistent research anchors",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResearchLinkWorkspace(
    document: VaultDocument,
    vaultRepository: DocumentVaultRepository,
    marginaliaRepository: MarginaliaRepository,
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
            Text(sessionResult.exceptionOrNull()?.message ?: "Unknown PDF error", color = MaterialTheme.colorScheme.error)
            Button(onClick = onBack) { Text("Back") }
        }
        return
    }

    val resolver = remember(session) { PdfPageTextResolver(session) }
    DisposableEffect(session, resolver) {
        onDispose {
            resolver.close()
            session.close()
        }
    }

    val allMarginalia by remember(document.id) {
        marginaliaRepository.observeItems(document.id)
    }.collectAsState(initial = emptyList())
    val researchAnchors = remember(allMarginalia) {
        allMarginalia
            .filter { it.kind == MarginaliaItemKind.RESEARCH_LINK }
            .sortedWith(
                compareBy<MarginaliaItem> { it.anchor.pageIndex }
                    .thenBy { it.anchor.startOffset ?: Int.MAX_VALUE },
            )
    }

    var pageIndex by remember(document.id) { mutableStateOf(0) }
    var pageText by remember(document.id) { mutableStateOf<PdfPageTextResult?>(null) }
    var pageTextReady by remember(document.id) { mutableStateOf(false) }
    var selectedAxisIds by remember(document.id) { mutableStateOf<Set<String>>(emptySet()) }
    var selectedProfileId by remember(document.id) { mutableStateOf<String?>(null) }
    var proximityScope by remember(document.id) { mutableStateOf(ProximityScope.CHARACTERS) }
    var proximityChars by remember(document.id) { mutableStateOf("300") }
    var focusedAnchorId by remember(document.id) { mutableStateOf<String?>(null) }
    var status by remember(document.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(savedAxes.map { it.id }) {
        selectedAxisIds = selectedAxisIds.intersect(savedAxes.map { it.id }.toSet())
    }

    LaunchedEffect(resolver, pageIndex) {
        pageTextReady = false
        pageText = runCatching { resolver.resolve(pageIndex) }.getOrElse {
            PdfPageTextResult("", PdfPageTextSource.NONE)
        }
        pageTextReady = true
    }

    val activeAxes = remember(savedAxes, selectedAxisIds) {
        savedAxes.filter { it.enabled && it.id in selectedAxisIds }
    }
    val lexicalAxes = remember(activeAxes) { activeAxes.map(ResearchAxisDefinition::toLexicalAxis) }
    val sourceText = pageText?.text.orEmpty()
    val hits = remember(sourceText, lexicalAxes) {
        LexicalSearchEngine.search(sourceText, lexicalAxes)
    }
    val intersections = remember(sourceText, hits, activeAxes, proximityScope, proximityChars) {
        if (sourceText.isEmpty() || activeAxes.size < 2) {
            emptyList()
        } else {
            LexicalSearchEngine.findProximityMatches(
                hits = hits,
                rules = listOf(
                    ProximityRule(
                        id = "marginalia-link-rule",
                        requiredAxisIds = activeAxes.map { it.id }.toSet(),
                        maxSpanChars = proximityChars.toIntOrNull()?.coerceAtLeast(0) ?: 300,
                        scope = proximityScope,
                    ),
                ),
                text = sourceText,
            )
        }
    }

    val focusedAnchor = remember(researchAnchors, focusedAnchorId, pageIndex) {
        researchAnchors.firstOrNull { item ->
            item.id == focusedAnchorId && item.anchor.pageIndex == pageIndex
        }
    }
    val overlayRequests = remember(activeAxes, hits, focusedAnchor) {
        val colors = activeAxes.associate { it.id to it.colorArgb }
        buildList {
            hits.forEach { hit ->
                colors[hit.axisId]?.let { color ->
                    add(
                        PdfTextHighlightRequest(
                            axisId = hit.axisId,
                            startOffset = hit.startOffset,
                            endOffsetExclusive = hit.endOffsetExclusive,
                            colorArgb = color,
                        ),
                    )
                }
            }
            val start = focusedAnchor?.anchor?.startOffset
            val end = focusedAnchor?.anchor?.endOffsetExclusive
            if (start != null && end != null && end > start) {
                add(
                    PdfTextHighlightRequest(
                        axisId = "focused-marginalia-anchor",
                        startOffset = start,
                        endOffsetExclusive = end,
                        colorArgb = 0xFFFF8F00L,
                    ),
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
                        "Page ${pageIndex + 1}/${session.pageCount} · ${researchAnchors.size} research anchor(s)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(
                    onClick = {
                        pageIndex = (pageIndex - 1).coerceAtLeast(0)
                        focusedAnchorId = null
                    },
                    enabled = pageIndex > 0,
                ) { Text("Prev") }
                TextButton(
                    onClick = {
                        pageIndex = (pageIndex + 1).coerceAtMost(session.pageCount - 1)
                        focusedAnchorId = null
                    },
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
            ResearchLinkAxesAndRuleCard(
                savedAxes = savedAxes,
                savedProfiles = savedProfiles,
                selectedAxisIds = selectedAxisIds,
                selectedProfileId = selectedProfileId,
                proximityScope = proximityScope,
                proximityChars = proximityChars,
                onSelectedAxisIdsChange = {
                    selectedAxisIds = it
                    selectedProfileId = null
                },
                onProfileUse = { profile ->
                    val valid = savedAxes.map { it.id }.toSet()
                    selectedAxisIds = profile.axisIds.filter { it in valid }.toSet()
                    selectedProfileId = profile.id
                    proximityScope = profile.proximityScope
                    proximityChars = profile.proximityChars.toString()
                    status = "Profile loaded."
                },
                onScopeChange = { proximityScope = it },
                onCharsChange = { proximityChars = it },
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("Current page results → Marginalia", fontWeight = FontWeight.Bold)
                    val sourceLabel = when (pageText?.source) {
                        PdfPageTextSource.NATIVE -> "native PDF text"
                        PdfPageTextSource.OCR -> "offline OCR"
                        PdfPageTextSource.NONE -> "no text"
                        null -> "loading"
                    }
                    Text(
                        "$sourceLabel · ${hits.size} hit(s) · ${intersections.size} intersection(s)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!pageTextReady) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    if (activeAxes.isEmpty()) {
                        Text(
                            "Activate saved axes. Create or edit axes in Rules & Cartography.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    hits.take(12).forEach { hit ->
                        val alreadyPinned = researchAnchors.any { item ->
                            item.anchor.pageIndex == pageIndex &&
                                item.anchor.startOffset == hit.startOffset &&
                                item.anchor.endOffsetExclusive == hit.endOffsetExclusive
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${hit.axisTitle}: “${hit.matchedText}”", style = MaterialTheme.typography.bodyMedium)
                                Text("offset ${hit.startOffset}–${hit.endOffsetExclusive}", style = MaterialTheme.typography.bodySmall)
                            }
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        val item = ResearchMarginaliaBridge.fromHit(
                                            id = UUID.randomUUID().toString(),
                                            documentId = document.id,
                                            pageIndex = pageIndex,
                                            pageText = sourceText,
                                            hit = hit,
                                        )
                                        marginaliaRepository.saveItem(item)
                                        focusedAnchorId = item.id
                                        status = "Research hit pinned to Marginalia."
                                    }
                                },
                                enabled = !alreadyPinned,
                            ) { Text(if (alreadyPinned) "Pinned" else "Pin hit") }
                        }
                    }

                    intersections.take(8).forEach { match ->
                        val alreadyPinned = researchAnchors.any { item ->
                            item.anchor.pageIndex == pageIndex &&
                                item.anchor.startOffset == match.startOffset &&
                                item.anchor.endOffsetExclusive == match.endOffsetExclusive
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Intersection · ${match.hits.map { it.axisTitle }.distinct().joinToString(" + ")}",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text("offset ${match.startOffset}–${match.endOffsetExclusive}", style = MaterialTheme.typography.bodySmall)
                            }
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        val item = ResearchMarginaliaBridge.fromIntersection(
                                            id = UUID.randomUUID().toString(),
                                            documentId = document.id,
                                            pageIndex = pageIndex,
                                            pageText = sourceText,
                                            match = match,
                                        )
                                        marginaliaRepository.saveItem(item)
                                        focusedAnchorId = item.id
                                        status = "Research intersection pinned to Marginalia."
                                    }
                                },
                                enabled = !alreadyPinned,
                            ) { Text(if (alreadyPinned) "Pinned" else "Pin intersection") }
                        }
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("Saved research anchors", fontWeight = FontWeight.Bold)
                    if (researchAnchors.isEmpty()) {
                        Text("No research anchors yet.", style = MaterialTheme.typography.bodySmall)
                    }
                    researchAnchors.take(80).forEach { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Page ${item.anchor.pageIndex + 1} · ${item.text.orEmpty().lineSequence().firstOrNull().orEmpty()}")
                                Text(
                                    "offset ${item.anchor.startOffset ?: 0}–${item.anchor.endOffsetExclusive ?: item.anchor.startOffset ?: 0}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(
                                onClick = {
                                    pageIndex = item.anchor.pageIndex.coerceIn(0, session.pageCount - 1)
                                    focusedAnchorId = item.id
                                    status = "Jumped to Marginalia anchor on page ${pageIndex + 1}."
                                },
                            ) { Text("Go") }
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        marginaliaRepository.deleteItem(item.id)
                                        if (focusedAnchorId == item.id) focusedAnchorId = null
                                        status = "Research anchor deleted."
                                    }
                                },
                            ) { Text("Delete") }
                        }
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("Source preview", fontWeight = FontWeight.Bold)
                    Text(
                        if (focusedAnchor != null) {
                            "Focused Marginalia anchor is highlighted in orange."
                        } else {
                            "Active lexical axes keep their saved colors."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ResearchLinkPreview(
                        session = session,
                        resolver = resolver,
                        pageIndex = pageIndex,
                        pageText = pageText,
                        overlayRequests = overlayRequests,
                    )
                }
            }

            status?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun ResearchLinkAxesAndRuleCard(
    savedAxes: List<ResearchAxisDefinition>,
    savedProfiles: List<ResearchProfile>,
    selectedAxisIds: Set<String>,
    selectedProfileId: String?,
    proximityScope: ProximityScope,
    proximityChars: String,
    onSelectedAxisIdsChange: (Set<String>) -> Unit,
    onProfileUse: (ResearchProfile) -> Unit,
    onScopeChange: (ProximityScope) -> Unit,
    onCharsChange: (String) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Saved axes & rule", fontWeight = FontWeight.Bold)
            savedAxes.forEach { axis ->
                val active = axis.id in selectedAxisIds
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(Color(axis.colorArgb.toInt()), RoundedCornerShape(2.dp)),
                    )
                    Text(axis.title, modifier = Modifier.weight(1f))
                    TextButton(
                        onClick = {
                            onSelectedAxisIdsChange(
                                if (active) selectedAxisIds - axis.id else selectedAxisIds + axis.id,
                            )
                        },
                    ) { Text(if (active) "Disable" else "Activate") }
                }
            }
            if (savedAxes.isEmpty()) {
                Text("No saved axes yet.", style = MaterialTheme.typography.bodySmall)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                ProximityScope.entries.forEach { ruleScope ->
                    TextButton(onClick = { onScopeChange(ruleScope) }) {
                        val label = when (ruleScope) {
                            ProximityScope.CHARACTERS -> "Chars"
                            ProximityScope.SENTENCE -> "Sentence"
                            ProximityScope.PARAGRAPH -> "Paragraph"
                            ProximityScope.PAGE -> "Page"
                        }
                        Text(if (ruleScope == proximityScope) "[$label]" else label)
                    }
                }
            }
            if (proximityScope == ProximityScope.CHARACTERS) {
                OutlinedTextField(
                    value = proximityChars,
                    onValueChange = { onCharsChange(it.filter(Char::isDigit).take(6)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Maximum intersection span") },
                    singleLine = true,
                )
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
                    TextButton(onClick = { onProfileUse(profile) }) { Text("Use") }
                }
            }
        }
    }
}

@Composable
private fun ResearchLinkPreview(
    session: PdfRendererSession,
    resolver: PdfPageTextResolver,
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
        LaunchedEffect(session, resolver, pageIndex, targetWidthPx, pageText, overlayRequests) {
            bitmap = null
            renderError = null
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val resolved = if (pageText == null) {
                        emptyList()
                    } else {
                        resolver.resolveHighlights(pageIndex, pageText, overlayRequests)
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
                Text("Rendering local source preview…", style = MaterialTheme.typography.bodySmall)
            }
            else -> {
                val imageWidth = with(density) { bitmap!!.width.toDp() }
                Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = "Research Marginalia source page ${pageIndex + 1}",
                    modifier = Modifier.width(imageWidth),
                )
            }
        }
    }
}

private fun VaultDocument.isPdfForResearchLinks(): Boolean =
    mimeType.equals("application/pdf", ignoreCase = true) ||
        VaultFileNames.extensionFrom(displayName) == "pdf"
