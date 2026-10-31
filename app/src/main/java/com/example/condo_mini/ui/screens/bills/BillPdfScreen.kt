package com.example.condo_mini.ui.screens.bills

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.condo_mini.ui.components.PdfViewerScreen
import com.example.condo_mini.ui.components.sharePdf
import com.example.condo_mini.ui.util.formatMonth
import java.io.File

@Composable
fun BillPdfRoute(
    billId: String,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BillPdfViewModel = viewModel(key = "bill-pdf-$billId") { BillPdfViewModel(billId) }
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    BillPdfScreen(
        uiState = uiState,
        onBackClick = onBackClick,
        onShareClick = { file ->
            val title = uiState.bill?.reference?.formatMonth().orEmpty()
            sharePdf(context, file, "Boleto do condomínio · $title", "Compartilhar boleto")
        },
        modifier = modifier
    )
}

@Composable
fun BillPdfScreen(
    uiState: BillPdfUiState,
    onBackClick: () -> Unit,
    onShareClick: (File) -> Unit,
    modifier: Modifier = Modifier
) {
    PdfViewerScreen(
        eyebrow = "Boleto em PDF",
        title = uiState.bill?.reference?.formatMonth() ?: "Boleto",
        isLoading = uiState.isLoading,
        error = uiState.error,
        pages = uiState.pages,
        pageLabel = "do boleto",
        onBackClick = onBackClick,
        onShareClick = uiState.file?.let { file -> { onShareClick(file) } },
        modifier = modifier
    )
}
