package com.reader.workspace.pdf

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.reader.workspace.research.ResearchAxisDefinition
import com.reader.workspace.research.ResearchHistoryEntry
import com.reader.workspace.research.ResearchHistoryScope
import com.reader.workspace.research.ResearchRepository
import java.util.UUID
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
internal fun DocumentResearchCartographyPanel(
    documentId: String,
    session: PdfRendererSession,
    textResolver: PdfPageTextResolver,
    pageCount: Int,
    activeAxes: List<ResearchAxisDefinition>,
    selectedProfileId: String?,
    proximityChars: Int,
    repository: ResearchRepository,
    onNavigateToPage: (Int) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var startPageText by remember(documentId, pageCount) { mutableStateOf("1") }
    var endPageText by remember(documentId, pageCount) { mutableStateOf(pageCount.coerceAtLeast(1).toString()) }
    var scanResult by remember(documentId) { mutableStateOf<PdfResearchScanResult?>(null) }
    var completedPages by remember(documentId) { mutableStateOf(0) }
    var totalPages by remember(documentId) { mutableStateOf(0) }
    var scanning by remember(documentId) { mutableStateOf(false) }
    var status by remember(documentId) { mutableStateOf<String?>(null) }
    var scanJob by remember(documentId) { mutableStateOf<Job?>(null) }

    val axisSignature = activeAxes.map { axis ->
        Triple(axis.id, axis.updatedAtEpochMillis, axis.matchMode)
    }

    LaunchedEffect(axisSignature, proximityChars) {
        if (scanning) scanJob?.cancel()
        scanResult = null
        completedPages = 0
        totalPages = 0
    }

    fun launchScan(
        range: IntRange,
        historyScope: ResearchHistoryScope,
    ) {
        if (scanning || activeAxes.isEmpty() || range.isEmpty()) return

        scanJob?.cancel()
        scanJob = scope.launch {
            scanning = true
            completedPages = 0
            totalPages = range.count()
            status = null
            try {
                val scanner = PdfDocumentResearchScanner(
                    session = session,
                    textResolver = textResolver,
                )
                val result = scanner.scan(
                    axes = activeAxes.map(ResearchAxisDefinition::toLexicalAxis),
                    pageRange = range,
                    proximityChars = proximityChars,
                ) { completed, total ->
                    completedPages = completed
                    totalPages = total
                }
                scanResult = result

                repository.recordHistory(
                    ResearchHistoryEntry(
                        id = UUID.randomUUID().toString(),
                        documentId = documentId,
                        pageIndex = result.startPageIndex,
                        profileId = selectedProfileId,
                        axisIds = activeAxes.map { it.id },
                        proximityChars = proximityChars,
                        hitCount = result.totalHits,
                        intersectionCount = result.totalIntersections,
                        executedAtEpochMillis = System.currentTimeMillis(),
                        scope = historyScope,
                        rangeStartPageIndex = result.startPageIndex,
                        rangeEndPageIndex = result.endPageIndex,
                    ),
                )
                status = when (historyScope) {
                    ResearchHistoryScope.DOCUMENT -> "Whole-document scan completed and saved to history."
                    ResearchHistoryScope.RANGE -> "Page-range scan completed and saved to history."
                    ResearchHistoryScope.PAGE -> "Page scan completed and saved to history."
                }
            } catch (cancelled: CancellationException) {
                status = "Document scan cancelled."
                throw cancelled
            } catch (error: Throwable) {
                status = "Document scan failed: ${error.message ?: error::class.simpleName.orEmpty()}"
            } finally {
                scanning = false
                scanJob = null
            }
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Document cartography",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Scan a page range or the whole PDF with the active lexical axes. Reader uses embedded text when available and bundled on-device OCR for scanned/image-only pages.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "OCR model is bundled in Reader; scanning does not need a network connection.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = startPageText,
                    onValueChange = { startPageText = it.filter(Char::isDigit).take(6) },
                    modifier = Modifier.weight(1f),
                    label = { Text("From page") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = endPageText,
                    onValueChange = { endPageText = it.filter(Char::isDigit).take(6) },
                    modifier = Modifier.weight(1f),
                    label = { Text("To page") },
                    singleLine = true,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = {
                        val start = startPageText.toIntOrNull() ?: 1
                        val end = endPageText.toIntOrNull() ?: pageCount
                        launchScan(
                            range = PdfResearchPageRange.resolve(pageCount, start, end),
                            historyScope = ResearchHistoryScope.RANGE,
                        )
                    },
                    enabled = activeAxes.isNotEmpty() && !scanning,
                ) {
                    Text("Scan range")
                }
                Button(
                    onClick = {
                        launchScan(
                            range = PdfResearchPageRange.resolve(pageCount, 1, pageCount),
                            historyScope = ResearchHistoryScope.DOCUMENT,
                        )
                    },
                    enabled = activeAxes.isNotEmpty() && !scanning,
                ) {
                    Text("Scan whole PDF")
                }
                if (scanning) {
                    TextButton(
                        onClick = {
                            scanJob?.cancel()
                            scanning = false
                            status = "Document scan cancelled."
                        },
                    ) {
                        Text("Cancel")
                    }
                }
            }

            if (scanning) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    text = "Scanning $completedPages / $totalPages pages locally…",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            status?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (message.contains("failed", ignoreCase = true)) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }

            scanResult?.let { result ->
                Text(
                    text = "${result.pageCount} page(s) · ${result.totalHits} hits · ${result.totalIntersections} intersections · ${result.pagesWithHits} page(s) with hits",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "Text sources: ${result.nativeTextPages} native · ${result.ocrPages} OCR · ${result.emptyTextPages} empty",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                val hitPages = result.pages.filter(PdfResearchPageScan::hasHits)
                if (hitPages.isEmpty()) {
                    Text(
                        text = "No matches in the scanned scope.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    Text(
                        text = "Page density map",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    hitPages.take(MAX_CARTOGRAPHY_ROWS).forEach { page ->
                        CartographyPageRow(
                            page = page,
                            result = result,
                            activeAxes = activeAxes,
                            onNavigate = { onNavigateToPage(page.pageIndex) },
                        )
                    }
                    if (hitPages.size > MAX_CARTOGRAPHY_ROWS) {
                        Text(
                            text = "Showing the first $MAX_CARTOGRAPHY_ROWS of ${hitPages.size} pages with hits.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CartographyPageRow(
    page: PdfResearchPageScan,
    result: PdfResearchScanResult,
    activeAxes: List<ResearchAxisDefinition>,
    onNavigate: () -> Unit,
) {
    val density = result.densityFraction(page)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    val sourceLabel = when (page.textState) {
                        PdfResearchTextState.AVAILABLE -> "native"
                        PdfResearchTextState.OCR -> "OCR"
                        PdfResearchTextState.EMPTY -> "empty"
                        PdfResearchTextState.UNSUPPORTED -> "unavailable"
                    }
                    Text(
                        text = "Page ${page.pageIndex + 1} · ${page.hitCount} hits · ${page.intersectionCount} intersections · $sourceLabel",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = "${densityBar(density)} ${(density * 100).roundToInt()}% relative density",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                TextButton(onClick = onNavigate) {
                    Text("Go to page")
                }
            }

            activeAxes.forEach { axis ->
                val count = page.axisHitCounts[axis.id] ?: 0
                if (count > 0) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(Color(axis.colorArgb.toInt()), RoundedCornerShape(2.dp)),
                        )
                        Text(
                            text = "${axis.title}: $count",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private fun densityBar(
    fraction: Float,
    width: Int = 12,
): String {
    val normalized = fraction.coerceIn(0f, 1f)
    val filled = (normalized * width).roundToInt().coerceIn(0, width)
    return "█".repeat(filled) + "░".repeat(width - filled)
}

private const val MAX_CARTOGRAPHY_ROWS = 60
