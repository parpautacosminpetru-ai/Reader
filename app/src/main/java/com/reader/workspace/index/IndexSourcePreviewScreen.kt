package com.reader.workspace.index

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.reader.workspace.pdf.PdfPageTextResolver
import com.reader.workspace.pdf.PdfRendererSession
import com.reader.workspace.pdf.PdfTextHighlightRequest
import com.reader.workspace.storage.DocumentVaultRepository
import com.reader.workspace.storage.VaultDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun IndexSourcePreviewScreen(
    document: VaultDocument,
    item: UniversalIndexItem,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val vaultRepository = remember(context.applicationContext) {
        DocumentVaultRepository.get(context.applicationContext)
    }
    val file = remember(document.id) { vaultRepository.localFile(document) }
    val sessionResult = remember(file.absolutePath) { runCatching { PdfRendererSession(file) } }
    val session = sessionResult.getOrNull()

    if (session == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Could not open source", style = MaterialTheme.typography.titleLarge)
            Text(sessionResult.exceptionOrNull()?.message ?: "Unknown PDF error")
            TextButton(onClick = onBack) { Text("Back to Index") }
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

    var pageIndex by remember(document.id, item.id) {
        mutableStateOf(item.pageIndex.coerceIn(0, session.pageCount - 1))
    }

    Scaffold(
        modifier = Modifier.safeDrawingPadding(),
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(onClick = onBack) { Text("Index") }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(document.displayName, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text(
                            "${item.title} · page ${pageIndex + 1}/${session.pageCount}",
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
                if (pageIndex == item.pageIndex && item.startOffset != null) {
                    Text(
                        "Indexed anchor ${item.startOffset}–${item.endOffsetExclusive ?: item.startOffset}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
    ) { innerPadding ->
        IndexPdfPage(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            session = session,
            resolver = resolver,
            pageIndex = pageIndex,
            anchor = if (pageIndex == item.pageIndex) item else null,
        )
    }
}

@Composable
private fun IndexPdfPage(
    modifier: Modifier,
    session: PdfRendererSession,
    resolver: PdfPageTextResolver,
    pageIndex: Int,
    anchor: UniversalIndexItem?,
) {
    val density = LocalDensity.current
    var bitmap by remember(session) { mutableStateOf<Bitmap?>(null) }
    var error by remember(session) { mutableStateOf<String?>(null) }

    BoxWithConstraints(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        val targetWidthPx = with(density) { maxWidth.roundToPx() }.coerceAtLeast(1)

        LaunchedEffect(session, resolver, pageIndex, targetWidthPx, anchor) {
            bitmap = null
            error = null
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val requests = if (
                        anchor?.startOffset != null &&
                        anchor.endOffsetExclusive != null &&
                        anchor.endOffsetExclusive > anchor.startOffset
                    ) {
                        listOf(
                            PdfTextHighlightRequest(
                                axisId = "universal-index",
                                startOffset = anchor.startOffset,
                                endOffsetExclusive = anchor.endOffsetExclusive,
                                colorArgb = 0xFFFF9800L,
                            ),
                        )
                    } else {
                        emptyList()
                    }

                    val resolvedHighlights = if (requests.isEmpty()) {
                        emptyList()
                    } else {
                        val pageText = resolver.resolve(pageIndex)
                        resolver.resolveHighlights(pageIndex, pageText, requests)
                    }
                    session.renderPage(pageIndex, targetWidthPx, resolvedHighlights)
                }
            }
            bitmap = result.getOrNull()
            error = result.exceptionOrNull()?.message
        }

        when {
            error != null -> Text(
                "Preview failed: $error",
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
                Text("Opening indexed source locally…")
            }
            else -> {
                val horizontal = rememberScrollState()
                val vertical = rememberScrollState()
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .horizontalScroll(horizontal)
                        .verticalScroll(vertical)
                        .padding(8.dp),
                ) {
                    Image(
                        bitmap = bitmap!!.asImageBitmap(),
                        contentDescription = "Indexed PDF page ${pageIndex + 1}",
                    )
                }
            }
        }
    }
}
