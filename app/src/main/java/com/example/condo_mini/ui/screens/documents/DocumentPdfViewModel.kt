package com.example.condo_mini.ui.screens.documents

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.condo_mini.data.AppContainer
import com.example.condo_mini.domain.model.CondoDocument
import com.example.condo_mini.domain.repository.DocumentRepository
import com.example.condo_mini.ui.util.renderPdfPages
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

data class DocumentPdfUiState(
    val isLoading: Boolean = true,
    val document: CondoDocument? = null,
    val file: File? = null,
    val pages: List<ImageBitmap> = emptyList(),
    val error: String? = null
)

class DocumentPdfViewModel(
    private val documentId: String,
    private val documentRepository: DocumentRepository = AppContainer.documentRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DocumentPdfUiState())
    val uiState: StateFlow<DocumentPdfUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val document = documentRepository.getDocument(documentId)
            if (document == null) {
                _uiState.value = DocumentPdfUiState(isLoading = false, error = "Documento não encontrado.")
                return@launch
            }
            runCatching {
                val file = documentRepository.documentFile(document)
                file to renderPdfPages(file)
            }.onSuccess { (file, pages) ->
                _uiState.value = DocumentPdfUiState(isLoading = false, document = document, file = file, pages = pages)
            }.onFailure {
                _uiState.value = DocumentPdfUiState(
                    isLoading = false,
                    document = document,
                    error = "Não foi possível abrir o PDF."
                )
            }
        }
    }
}
