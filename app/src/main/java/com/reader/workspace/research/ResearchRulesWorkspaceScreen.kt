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
fun ResearchRulesWorkspaceScreen(onBack: () -> Unit) {
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
        RulesDocumentPicker(
            documents = documents.filter(VaultDocument::isPdfForRulesResearch),
            savedAxisCount = savedAxes.size,
            savedProfileCount = savedProfiles.size,
            onBack = onBack,
            onSelect = { selectedDocument = it },
        )
    } else {
        RulesDocumentWorkspace(
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
private fun RulesDocumentPicker(
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
                        "$savedAxisCount axes · $savedProfileCount profiles · structural rules · offline OCR",
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
                "Choose a PDF. Research remains lexical and deterministic: native text first, bundled OCR when needed.",
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
                                "Local PDF · rule-aware lexical cartography",
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
private fun RulesDocumentWorkspace(
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
    var proximityScope by remember(document.id) { mutableStateOf(ProximityScope.CHARACTERS) }
    var pageIndex by remember(document.id) { mutableStateOf(0) }
    var pageText by remember(document.id) { mutableStateOf<PdfPageTextResult?>(null) }
    var pageTextReady by remember(document.id) { mutableStateOf(false) }
    var newAxisTitle by remember { mutableStateOf("") }
    var newAxisForms by remember { mutableStateOf("") }
    var newAxisMode by remember { mutableStateOf(LexicalMatchMode.PREFIX) }
    var newAxisSuffix by remember { mutableStateOf(false) }
    var newAxisCaseSensitive by remember { mutableStateOf(false) }
    var newAxisDiacriticsSensitive by remember { mutableStateOf(true) }
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
    val intersections = remember(pageText, hits, activeAxes, proximityText, proximityScope) {
        val text = pageText?.text.orEmpty()
        if (activeAxes.size < 2 || text.isEmpty()) {
            emptyList()
        } else {
            LexicalSearchEngine.findProximityMatches(
                hits = hits,
                rules = listOf(
                    ProximityRule(
                        id = "page-rule",
                        requiredAxisIds = activeAxes.map { it.id }.toSet(),
                        maxSpanChars = proximityText.toIntOrNull()?.coerceAtLeast(0) ?: 300,
                        scope = proximityScope,
                    ),
                ),
                text = text,
            )
        }
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
                        "Research 0.9 · page ${pageIndex + 1}/${session.pageCount} · ${activeAxes.size} active axis/axes",
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
                    Text("Axes", fontWeight = FontWeight.Bold)
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
                        label = { Text("Lexical forms or punctuation · comma/newline separated") },
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        AxisCreationMode.entries.forEach { mode ->
                            val selected = when (mode) {
                                AxisCreationMode.EXACT -> newAxisMode == LexicalMatchMode.EXACT && !newAxisSuffix
                                AxisCreationMode.PREFIX -> newAxisMode == LexicalMatchMode.PREFIX && !newAxisSuffix
                                AxisCreationMode.SUFFIX -> newAxisSuffix
                                AxisCreationMode.CONTAINS -> newAxisMode == LexicalMatchMode.CONTAINS && !newAxisSuffix
                            }
                            TextButton(
                                onClick = {
                                    when (mode) {
                                        AxisCreationMode.EXACT -> {
                                            newAxisMode = LexicalMatchMode.EXACT
                                            newAxisSuffix = false
                                        }
                                        AxisCreationMode.PREFIX -> {
                                            newAxisMode = LexicalMatchMode.PREFIX
                                            newAxisSuffix = false
                                        }
                                        AxisCreationMode.SUFFIX -> {
                                            newAxisMode = LexicalMatchMode.CONTAINS
                                            newAxisSuffix = true
                                        }
                                        AxisCreationMode.CONTAINS -> {
                                            newAxisMode = LexicalMatchMode.CONTAINS
                                            newAxisSuffix = false
                                        }
                                    }
                                },
                            ) {
                                Text(if (selected) "[${mode.label}]" else mode.label)
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(onClick = { newAxisCaseSensitive = !newAxisCaseSensitive }) {
                            Text(if (newAxisCaseSensitive) "Case: exact" else "Case: ignore")
                        }
                        TextButton(onClick = { newAxisDiacriticsSensitive = !newAxisDiacriticsSensitive }) {
                            Text(if (newAxisDiacriticsSensitive) "Diacritics: exact" else "Diacritics: ignore")
                        }
                    }

                    Button(
                        onClick = {
                            val title = newAxisTitle.trim()
                            val patterns = rulesSplitPatterns(newAxisForms)
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
                                            caseSensitive = newAxisCaseSensitive,
                                            diacriticsSensitive = newAxisDiacriticsSensitive,
                                            suffixMatch = newAxisSuffix,
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
                        enabled = newAxisTitle.isNotBlank() && rulesSplitPatterns(newAxisForms).isNotEmpty(),
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
                                    "${axisModeLabel(axis)} · ${if (axis.caseSensitive) "case exact" else "case ignored"} · ${if (axis.diacriticsSensitive) "diacritics exact" else "diacritics ignored"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    axis.patterns.joinToString(limit = 6, truncated = "…"),
                                    style = MaterialTheme.typography.bodySmall,
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
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Intersection rule", fontWeight = FontWeight.Bold)
                    Text(
                        "All active axes must co-occur inside the selected deterministic scope.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        ProximityScope.entries.forEach { ruleScope ->
                            TextButton(onClick = { proximityScope = ruleScope }) {
                                val label = ruleScopeShortLabel(ruleScope)
                                Text(if (proximityScope == ruleScope) "[$label]" else label)
                            }
                        }
                    }
                    if (proximityScope == ProximityScope.CHARACTERS) {
                        OutlinedTextField(
                            value = proximityText,
                            onValueChange = { proximityText = it.filter(Char::isDigit).take(6) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Maximum span in characters") },
                            singleLine = true,
                        )
                    } else {
                        Text(
                            ruleScopeLongLabel(proximityScope),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }

                    Text(
                        "Current page: ${hits.size} hits · ${intersections.size} intersection(s)",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    intersections.take(6).forEach { match ->
                        Text(
                            "${match.startOffset}–${match.endOffsetExclusive} · ${match.hits.map { it.axisTitle }.distinct().joinToString(" + ")}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

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
                                                proximityScope = proximityScope,
                                            ),
                                        )
                                        selectedProfileId = id
                                        profileTitle = ""
                                        status = "Profile saved with ${ruleScopeShortLabel(proximityScope)} rule."
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
                            Column(modifier = Modifier.weight(1f)) {
                                Text(if (selectedProfileId == profile.id) "✓ ${profile.title}" else profile.title)
                                Text(
                                    "${profile.axisIds.size} axes · ${profileRuleLabel(profile)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(
                                onClick = {
                                    val validIds = savedAxes.map { it.id }.toSet()
                                    selectedAxisIds = profile.axisIds.filter { it in validIds }.toSet()
                                    selectedProfileId = profile.id
                                    proximityText = profile.proximityChars.toString()
                                    proximityScope = profile.proximityScope
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
                            scope.launch {
                                repository.recordHistory(
                                    ResearchHistoryEntry(
                                        id = UUID.randomUUID().toString(),
                                        documentId = document.id,
                                        pageIndex = pageIndex,
                                        profileId = selectedProfileId,
                                        axisIds = activeAxes.map { it.id },
                                        proximityChars = proximityText.toIntOrNull()?.coerceAtLeast(0) ?: 300,
                                        hitCount = hits.size,
                                        intersectionCount = intersections.size,
                                        executedAtEpochMillis = System.currentTimeMillis(),
                                        proximityScope = proximityScope,
                                    ),
                                )
                                status = "Current page search saved to history."
                            }
                        },
                        enabled = activeAxes.isNotEmpty() && pageTextReady,
                    ) { Text("Save page search") }
                }
            }

            DocumentResearchCartographyPanel(
                documentId = document.id,
                session = session,
                textResolver = textResolver,
                pageCount = session.pageCount,
                activeAxes = activeAxes,
                selectedProfileId = selectedProfileId,
                proximityChars = proximityText.toIntOrNull()?.coerceAtLeast(0) ?: 300,
                proximityScope = proximityScope,
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
                                "$source · ${hits.size} hits · ${intersections.size} intersections",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    RulesResearchPagePreview(
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
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text("Recent research history", fontWeight = FontWeight.Bold)
                        history.take(10).forEach { entry ->
                            Text(
                                "${entry.scopeLabel()} · ${entry.ruleLabel()} · ${entry.hitCount} hits · ${entry.intersectionCount} intersections",
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
private fun RulesResearchPagePreview(
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
                    contentDescription = "Rule-aware research preview page ${pageIndex + 1}",
                    modifier = Modifier.width(imageWidth),
                )
            }
        }
    }
}

private enum class AxisCreationMode(val label: String) {
    EXACT("Exact"),
    PREFIX("Stem"),
    SUFFIX("Suffix"),
    CONTAINS("Contains"),
}

private fun axisModeLabel(axis: ResearchAxisDefinition): String = when {
    axis.suffixMatch -> "Suffix"
    axis.matchMode == LexicalMatchMode.EXACT -> "Exact"
    axis.matchMode == LexicalMatchMode.PREFIX -> "Stem"
    else -> "Contains"
}

private fun ruleScopeShortLabel(scope: ProximityScope): String = when (scope) {
    ProximityScope.CHARACTERS -> "Chars"
    ProximityScope.SENTENCE -> "Sentence"
    ProximityScope.PARAGRAPH -> "Paragraph"
    ProximityScope.PAGE -> "Page"
}

private fun ruleScopeLongLabel(scope: ProximityScope): String = when (scope) {
    ProximityScope.CHARACTERS -> "All active axes must fit inside the selected character span."
    ProximityScope.SENTENCE -> "All active axes must occur in the same sentence."
    ProximityScope.PARAGRAPH -> "All active axes must occur in the same paragraph."
    ProximityScope.PAGE -> "All active axes must occur somewhere on the same page."
}

private fun profileRuleLabel(profile: ResearchProfile): String = when (profile.proximityScope) {
    ProximityScope.CHARACTERS -> "within ${profile.proximityChars} chars"
    ProximityScope.SENTENCE -> "same sentence"
    ProximityScope.PARAGRAPH -> "same paragraph"
    ProximityScope.PAGE -> "same page"
}

private fun VaultDocument.isPdfForRulesResearch(): Boolean =
    mimeType.equals("application/pdf", ignoreCase = true) ||
        VaultFileNames.extensionFrom(displayName) == "pdf"

private fun rulesSplitPatterns(raw: String): List<String> = raw
    .split(',', ';', '\n')
    .map(String::trim)
    .filter(String::isNotEmpty)
    .distinct()
