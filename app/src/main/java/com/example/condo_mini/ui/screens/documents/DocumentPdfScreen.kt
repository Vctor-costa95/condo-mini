package com.example.condo_mini.ui.screens.documents

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.condo_mini.ui.components.PdfViewerScreen
import com.example.condo_mini.ui.components.sharePdf

@Composable
fun DocumentPdfRoute(
    documentId: String,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DocumentPdfViewModel = viewModel(key = "document-pdf-$documentId") { DocumentPdfViewModel(documentId) }
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val document = uiState.document
    PdfViewerScreen(
        eyebrow = document?.category?.sectionTitle ?: "Documento",
        title = document?.title ?: "Documento",
        isLoading = uiState.isLoading,
        error = uiState.error,
        pages = uiState.pages,
        pageLabel = "do documento",
        onBackClick = onBackClick,
        onShareClick = uiState.file?.let { file ->
            { sharePdf(context, file, "${document?.title.orEmpty()} · Condomínio", "Compartilhar documento") }
        },
        modifier = modifier
    )
}
