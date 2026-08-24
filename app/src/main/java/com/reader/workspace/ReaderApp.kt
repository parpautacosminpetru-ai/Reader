package com.reader.workspace

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.reader.workspace.core.ModuleCatalog
import com.reader.workspace.core.ReaderModule
import com.reader.workspace.pdf.PdfReaderWorkspaceScreen
import com.reader.workspace.research.ResearchRulesWorkspaceScreen
import com.reader.workspace.storage.DocumentsScreen
import com.reader.workspace.storage.VaultDocument

@Composable
fun ReaderApp() {
    var selectedModule by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedDocument by remember { mutableStateOf<VaultDocument?>(null) }

    MaterialTheme {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
        ) {
            when {
                selectedDocument != null -> PdfReaderWorkspaceScreen(
                    document = selectedDocument!!,
                    onBack = { selectedDocument = null },
                )
                selectedModule == "documents" -> DocumentsScreen(
                    onBack = { selectedModule = null },
                    onOpenDocument = { document -> selectedDocument = document },
                )
                selectedModule == "research" -> ResearchRulesWorkspaceScreen(
                    onBack = { selectedModule = null },
                )
                else -> HomeScreen(onOpenModule = { moduleId ->
                    if (moduleId == "documents" || moduleId == "research") {
                        selectedModule = moduleId
                    }
                })
            }
        }
    }
}

@Composable
private fun HomeScreen(onOpenModule: (String) -> Unit) {
    Scaffold(
        topBar = { ReaderHeader() },
    ) { innerPadding ->
        ModuleGrid(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            onOpenModule = onOpenModule,
        )
    }
}

@Composable
private fun ReaderHeader() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "Reader",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Offline workspace · Structural Research 0.9",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ModuleGrid(
    modifier: Modifier = Modifier,
    onOpenModule: (String) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 260.dp),
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(ModuleCatalog.modules, key = { it.id }) { module ->
            ModuleCard(
                module = module,
                onClick = { onOpenModule(module.id) },
            )
        }
    }
}

@Composable
private fun ModuleCard(
    module: ReaderModule,
    onClick: () -> Unit,
) {
    val enabled = module.id == "documents" || module.id == "research"
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = module.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = module.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = if (enabled) "Open · ${module.status}" else module.status,
                style = MaterialTheme.typography.labelMedium,
                color = if (enabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 720)
@Composable
private fun ReaderAppPreview() {
    ReaderApp()
}
