package com.example.condo_mini.ui.screens.documents

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.HistoryEdu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.condo_mini.domain.model.CondoDocument
import com.example.condo_mini.domain.model.DocumentCategory
import com.example.condo_mini.ui.components.CondoCard
import com.example.condo_mini.ui.components.EmptyState
import com.example.condo_mini.ui.components.IconBadge
import com.example.condo_mini.ui.components.LoadingState
import com.example.condo_mini.ui.components.SectionHeader
import com.example.condo_mini.ui.theme.CondominiTheme
import com.example.condo_mini.ui.util.formatDate
import com.example.condo_mini.ui.util.formatFileSize
import java.time.LocalDateTime

@Composable
fun DocumentsRoute(
    onOpenDocument: (CondoDocument) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DocumentsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    DocumentsScreen(uiState = uiState, onOpenDocument = onOpenDocument, modifier = modifier)
}

@Composable
fun DocumentsScreen(
    uiState: DocumentsUiState,
    onOpenDocument: (CondoDocument) -> Unit,
    modifier: Modifier = Modifier
) {
    when {
        uiState.isLoading -> LoadingState(modifier)
        uiState.documents.isEmpty() -> EmptyState(
            icon = Icons.Outlined.FolderOpen,
            title = "Nenhum documento ainda",
            message = "O regimento, a convenção e as atas de assembleia aparecem aqui assim que o síndico publicar.",
            modifier = modifier
        )

        else -> LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    "Regimento, convenção e atas de assembleia publicados pelo síndico. Toque para abrir o PDF.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                )
            }
            documentSections(uiState.documents, onOpenDocument)
        }
    }
}

/**
 * Lista agrupada por tipo, na ordem de [DocumentCategory]; dentro de cada grupo,
 * do mais recente para o mais antigo. Usada pelo morador e pelo síndico.
 */
fun LazyListScope.documentSections(
    documents: List<CondoDocument>,
    onOpenDocument: (CondoDocument) -> Unit,
    footer: (@Composable (CondoDocument) -> Unit)? = null
) {
    val byCategory = documents.groupBy { it.category }
    DocumentCategory.entries.forEach { category ->
        val group = byCategory[category].orEmpty().sortedByDescending { it.uploadedAt }
        if (group.isEmpty()) return@forEach
        item(key = "header-${category.name}") {
            SectionHeader(
                category.sectionTitle,
                modifier = Modifier.padding(horizontal = 4.dp),
                trailing = if (group.size > 1) "${group.size}" else null
            )
        }
        items(group, key = { it.id }) { document ->
            DocumentCard(document, onClick = { onOpenDocument(document) }, footer = footer)
        }
    }
}

@Composable
private fun DocumentCard(
    document: CondoDocument,
    onClick: () -> Unit,
    footer: (@Composable (CondoDocument) -> Unit)?
) {
    CondoCard(onClick = onClick, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon = document.category.icon(), size = 44.dp)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    document.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "Publicado em ${document.uploadedAt.toLocalDate().formatDate()} · ${document.sizeBytes.formatFileSize()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Abrir",
                tint = MaterialTheme.colorScheme.outline
            )
        }
        footer?.let { content ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 58.dp)
            ) { content(document) }
        }
    }
}

fun DocumentCategory.icon(): ImageVector = when (this) {
    DocumentCategory.BYLAWS -> Icons.AutoMirrored.Outlined.MenuBook
    DocumentCategory.CONVENTION -> Icons.Outlined.Gavel
    DocumentCategory.MINUTES -> Icons.Outlined.HistoryEdu
    DocumentCategory.OTHER -> Icons.Outlined.Description
}

@Preview(showBackground = true)
@Composable
private fun DocumentsScreenPreview() {
    val now = LocalDateTime.now()
    CondominiTheme {
        DocumentsScreen(
            uiState = DocumentsUiState(
                isLoading = false,
                documents = listOf(
                    CondoDocument("1", "c1", "Regimento interno", DocumentCategory.BYLAWS, "r.pdf", "", 320_000, now, "Síndico"),
                    CondoDocument("2", "c1", "Ata da AGO · prestação de contas", DocumentCategory.MINUTES, "a.pdf", "", 128_000, now, "Síndico")
                )
            ),
            onOpenDocument = {}
        )
    }
}
