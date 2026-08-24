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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import com.reader.workspace.research.LexicalAxis
import com.reader.workspace.research.LexicalHit
import com.reader.workspace.research.LexicalMatchMode
import com.reader.workspace.research.LexicalSearchEngine
import com.reader.workspace.research.ProximityMatch
import com.reader.workspace.research.ProximityRule
import com.reader.workspace.storage.DocumentVaultRepository
import com.reader.workspace.storage.VaultDocument
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PdfReaderScreen(
    document: VaultDocument,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val vaultRepository = remember(context.applicationContext) {
        DocumentVaultRepository.get(context.applicationContext)
    }
    val file = remember(document.id) { vaultRepository.localFile(document) }
    val sessionResult = remember(file.absolutePath) { runCatching { PdfRendererSession(file) } }
    val session = sessionResult.getOrNull()

    DisposableEffect(session) {
        onDispose { session?.close() }
    }

    if (session == null) {
        PdfOpenError(
            documentName = document.displayName,
            message = sessionResult.exceptionOrNull()?.message ?: "Unable to open PDF",
            onBack = onBack,
        )
        return
    }

    var pageIndex by remember(document.id) { mutableStateOf(0) }
    var zoom by remember(document.id) { mutableFloatStateOf(1f) }
    var showMarginalia by remember(document.id) { mutableStateOf(false) }
    var showResearch by remember(document.id) { mutableStateOf(false) }
    var pageText by remember(document.id) { mutableStateOf<String?>(null) }
    var pageTextReady by remember(document.id) { mutableStateOf(false) }

    LaunchedEffect(session, pageIndex) {
        pageTextReady = false
        pageText = withContext(Dispatchers.IO) {
            runCatching { session.extractPageText(pageIndex) }.getOrNull()
        }
        pageTextReady = true
    }

    Scaffold(
        modifier = Modifier.safeDrawingPadding(),
        topBar = {
            PdfReaderHeader(
                documentName = document.displayName,
                pageIndex = pageIndex,
                pageCount = session.pageCount,
                zoom = zoom,
                showMarginalia = showMarginalia,
                showResearch = showResearch,
                onBack = onBack,
                onPrevious = { pageIndex = (pageIndex - 1).coerceAtLeast(0) },
                onNext = { pageIndex = (pageIndex + 1).coerceAtMost(session.pageCount - 1) },
                onZoomOut = { zoom = (zoom - 0.25f).coerceAtLeast(0.75f) },
                onZoomIn = { zoom = (zoom + 0.25f).coerceAtMost(3f) },
                onToggleMarginalia = { showMarginalia = !showMarginalia },
                onToggleResearch = { showResearch = !showResearch },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (showResearch) {
                ResearchPanel(
                    pageNumber = pageIndex + 1,
                    pageText = pageText,
                    pageTextReady = pageTextReady,
                )
            }

            PdfAndMarginaliaArea(
                modifier = Modifier.weight(1f),
                document = document,
                session = session,
                pageIndex = pageIndex,
                zoom = zoom,
                pageText = pageText.orEmpty(),
                showMarginalia = showMarginalia,
            )
        }
    }
}

@Composable
private fun PdfReaderHeader(
    documentName: String,
    pageIndex: Int,
    pageCount: Int,
    zoom: Float,
    showMarginalia: Boolean,
    showResearch: Boolean,
    onBack: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onZoomOut: () -> Unit,
    onZoomIn: () -> Unit,
    onToggleMarginalia: () -> Unit,
    onToggleResearch: () -> Unit,
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
                    text = "Page ${pageIndex + 1} / $pageCount · ${(zoom * 100).toInt()}% · Offline",
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
        }
    }
}

@Composable
private fun PdfAndMarginaliaArea(
    modifier: Modifier,
    document: VaultDocument,
    session: PdfRendererSession,
    pageIndex: Int,
    zoom: Float,
    pageText: String,
    showMarginalia: Boolean,
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
        PdfPageViewport(
            modifier = Modifier.weight(pdfWeight),
            session = session,
            pageIndex = pageIndex,
            zoom = zoom,
        )

        if (showMarginalia) {
            MarginaliaPanel(
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
private fun PdfPageViewport(
    modifier: Modifier,
    session: PdfRendererSession,
    pageIndex: Int,
    zoom: Float,
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

        LaunchedEffect(session, pageIndex, targetWidthPx) {
            bitmap = null
            renderError = null
            val result = withContext(Dispatchers.IO) {
                runCatching { session.renderPage(pageIndex, targetWidthPx) }
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
                Text("Rendering page locally…")
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
private fun MarginaliaPanel(
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
    val anchorRange = MarginaliaGeometry.anchorVerticalRange(anchor, pageText.length)
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
        ) {
            Text("Save text marker")
        }

        status?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Text(
            "Anchor range ${(anchorRange.topFraction * 100).toInt()}–${(anchorRange.bottomFraction * 100).toInt()}%",
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            "${pageItems.size} saved marker(s) on this page · source PDF unchanged",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        pageItems.forEach { item ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(item.kind.name, style = MaterialTheme.typography.labelMedium)
                    Text(item.text.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                    TextButton(
                        onClick = {
                            scope.launch {
                                repository.deleteItem(item.id)
                                status = "Marker deleted."
                            }
                        },
                    ) {
                        Text("Delete marker")
                    }
                }
            }
        }
    }
}

@Composable
private fun ResearchPanel(
    pageNumber: Int,
    pageText: String?,
    pageTextReady: Boolean,
) {
    var axisA by remember { mutableStateOf("") }
    var axisB by remember { mutableStateOf("") }
    var matchMode by remember { mutableStateOf(LexicalMatchMode.PREFIX) }
    var proximityChars by remember { mutableStateOf("300") }

    val sourceText = pageText.orEmpty()
    val axes = remember(axisA, axisB, matchMode) {
        buildList {
            val first = splitPatterns(axisA)
            if (first.isNotEmpty()) add(LexicalAxis("axis-a", "Axis A", first, matchMode))
            val second = splitPatterns(axisB)
            if (second.isNotEmpty()) add(LexicalAxis("axis-b", "Axis B", second, matchMode))
        }
    }
    val hits = remember(sourceText, axes) { LexicalSearchEngine.search(sourceText, axes) }
    val proximity = remember(hits, axes, proximityChars) {
        if (axes.size < 2) {
            emptyList()
        } else {
            LexicalSearchEngine.findProximityMatches(
                hits = hits,
                rules = listOf(
                    ProximityRule(
                        id = "ui-intersection",
                        requiredAxisIds = axes.map { it.id }.toSet(),
                        maxSpanChars = proximityChars.toIntOrNull()?.coerceAtLeast(0) ?: 300,
                    ),
                ),
            )
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 300.dp)
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Lexical Research · page $pageNumber", style = MaterialTheme.typography.titleMedium)

            when {
                !pageTextReady -> {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text("Reading page text locally…", style = MaterialTheme.typography.bodySmall)
                }
                pageText == null -> Text(
                    "Native PDF text extraction is unavailable here. OCR fallback is a later step.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                pageText.isEmpty() -> Text(
                    "No embedded text was found on this page. It may be scanned/image-only.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LexicalMatchMode.entries.forEach { mode ->
                    TextButton(onClick = { matchMode = mode }) {
                        Text(if (matchMode == mode) "[${modeLabel(mode)}]" else modeLabel(mode))
                    }
                }
            }

            OutlinedTextField(
                value = axisA,
                onValueChange = { axisA = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Axis A forms") },
            )
            OutlinedTextField(
                value = axisB,
                onValueChange = { axisB = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Axis B forms (optional)") },
            )
            OutlinedTextField(
                value = proximityChars,
                onValueChange = { proximityChars = it.filter(Char::isDigit).take(6) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Maximum intersection span (characters)") },
                singleLine = true,
            )

            Text(
                "${hits.size} hit(s) · ${proximity.size} dense intersection(s)",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            ResearchResults(hits = hits, proximity = proximity)
        }
    }
}

@Composable
private fun ResearchResults(
    hits: List<LexicalHit>,
    proximity: List<ProximityMatch>,
) {
    if (hits.isNotEmpty()) {
        Text("Hits", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        hits.take(12).forEach { hit ->
            Text(
                "${hit.axisTitle}: “${hit.matchedText}” @ ${hit.startOffset}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }

    if (proximity.isNotEmpty()) {
        Text("Intersections", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        proximity.take(8).forEach { match ->
            Text(
                "span ${match.startOffset}–${match.endOffsetExclusive} · " +
                    match.hits.map { it.axisTitle }.distinct().joinToString(" + "),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun splitPatterns(raw: String): List<String> = raw
    .split(',', ';', '\n')
    .map(String::trim)
    .filter(String::isNotEmpty)
    .distinct()

private fun modeLabel(mode: LexicalMatchMode): String = when (mode) {
    LexicalMatchMode.EXACT -> "Exact"
    LexicalMatchMode.PREFIX -> "Stem"
    LexicalMatchMode.CONTAINS -> "Contains"
}

@Composable
private fun PdfOpenError(
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
