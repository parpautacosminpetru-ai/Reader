package com.reader.workspace.pdf

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.reader.workspace.research.ResearchAxisDefinition
import com.reader.workspace.research.ResearchRepository

@Composable
internal fun DocumentResearchCartographyPanel(
    documentId: String,
    session: PdfRendererSession,
    pageCount: Int,
    activeAxes: List<ResearchAxisDefinition>,
    selectedProfileId: String?,
    proximityChars: Int,
    repository: ResearchRepository,
    onNavigateToPage: (Int) -> Unit,
) {
    val textResolver = remember(session) { PdfPageTextResolver(session) }
    DisposableEffect(textResolver) {
        onDispose { textResolver.close() }
    }

    DocumentResearchCartographyPanel(
        documentId = documentId,
        session = session,
        textResolver = textResolver,
        pageCount = pageCount,
        activeAxes = activeAxes,
        selectedProfileId = selectedProfileId,
        proximityChars = proximityChars,
        repository = repository,
        onNavigateToPage = onNavigateToPage,
    )
}
