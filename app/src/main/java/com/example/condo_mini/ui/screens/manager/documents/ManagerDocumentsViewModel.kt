package com.example.condo_mini.ui.screens.manager.documents

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.condo_mini.data.AppContainer
import com.example.condo_mini.domain.model.CondoDocument
import com.example.condo_mini.domain.model.DocumentCategory
import com.example.condo_mini.domain.repository.DocumentRepository
import com.example.condo_mini.domain.repository.SessionRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DocumentForm(
    val category: DocumentCategory = DocumentCategory.MINUTES,
    val title: String = "",
    val fileUri: String? = null,
    val fileName: String? = null,
    /** Regimento e convenção têm uma versão vigente: a nova pode substituir a anterior. */
    val replaceCurrent: Boolean = true,
    val isSubmitting: Boolean = false,
    val error: String? = null
) {
    val isSingleVersion: Boolean
        get() = category == DocumentCategory.BYLAWS || category == DocumentCategory.CONVENTION
}

data class ManagerDocumentsUiState(
    val isLoading: Boolean = true,
    val documents: List<CondoDocument> = emptyList(),
    val form: DocumentForm? = null,
    val userMessage: String? = null
) {
    /** Versão vigente que a nova publicação substituiria, se houver. */
    val currentOfFormCategory: CondoDocument?
        get() = form?.takeIf { it.isSingleVersion }?.let { f ->
            documents.filter { it.category == f.category }.maxByOrNull { it.uploadedAt }
        }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ManagerDocumentsViewModel(
    private val documentRepository: DocumentRepository = AppContainer.documentRepository,
    private val sessionRepository: SessionRepository = AppContainer.sessionRepository
) : ViewModel() {

    private val form = MutableStateFlow<DocumentForm?>(null)
    private val userMessage = MutableStateFlow<String?>(null)

    private val documents = sessionRepository.currentUser.flatMapLatest { user ->
        if (user == null) flowOf(emptyList()) else documentRepository.observeDocuments(user.condoId)
    }

    val uiState: StateFlow<ManagerDocumentsUiState> =
        combine(documents, form, userMessage) { documents, form, message ->
            ManagerDocumentsUiState(isLoading = false, documents = documents, form = form, userMessage = message)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ManagerDocumentsUiState())

    fun onNewDocument() {
        form.value = DocumentForm()
    }

    fun onDismissForm() {
        if (form.value?.isSubmitting == true) return
        form.value = null
    }

    fun onCategoryChanged(category: DocumentCategory) = form.update { current ->
        current ?: return@update null
        // Título sugerido acompanha a categoria enquanto o síndico não escreveu outro.
        val keepsSuggestion = current.title.isBlank() || current.title == defaultTitle(current.category)
        current.copy(
            category = category,
            title = if (keepsSuggestion) defaultTitle(category) else current.title,
            error = null
        )
    }

    fun onTitleChanged(title: String) = form.update { it?.copy(title = title.take(MAX_TITLE), error = null) }

    fun onReplaceCurrentChanged(replace: Boolean) = form.update { it?.copy(replaceCurrent = replace) }

    /** [uri] vem do seletor de documentos do sistema; [name] é o nome do arquivo, quando disponível. */
    fun onPdfPicked(uri: String, name: String?) = form.update { current ->
        current?.copy(
            fileUri = uri,
            fileName = name ?: "Documento.pdf",
            title = current.title.ifBlank { name?.substringBeforeLast('.').orEmpty().take(MAX_TITLE) },
            error = null
        )
    }

    fun onPublish() {
        val current = form.value ?: return
        val uri = current.fileUri
        val error = when {
            uri == null -> "Escolha o arquivo PDF."
            current.title.isBlank() -> "Informe um título."
            else -> null
        }
        if (uri == null || error != null) {
            form.update { it?.copy(error = error) }
            return
        }
        val user = sessionRepository.currentUser.value ?: return
        val replaced = uiState.value.currentOfFormCategory?.takeIf { current.replaceCurrent }
        form.update { it?.copy(isSubmitting = true) }
        viewModelScope.launch {
            val name = user.name
            documentRepository.upload(
                condoId = user.condoId,
                title = current.title,
                category = current.category,
                sourceUri = uri,
                uploadedBy = if (name.isBlank()) "Síndico" else "Síndico - $name"
            ).onSuccess {
                replaced?.let { old -> documentRepository.delete(old.id) }
                form.value = null
                userMessage.value = if (replaced != null) {
                    "Nova versão de “${current.category.sectionTitle}” publicada para os moradores."
                } else {
                    "Documento publicado para todos os moradores."
                }
            }.onFailure { failure ->
                form.update {
                    it?.copy(isSubmitting = false, error = failure.message ?: "Não foi possível enviar o PDF.")
                }
            }
        }
    }

    fun onDelete(document: CondoDocument) {
        viewModelScope.launch {
            documentRepository.delete(document.id)
            userMessage.value = "“${document.title}” foi removido."
        }
    }

    fun onMessageShown() {
        userMessage.value = null
    }

    private fun defaultTitle(category: DocumentCategory): String = when (category) {
        DocumentCategory.BYLAWS, DocumentCategory.CONVENTION -> category.sectionTitle
        DocumentCategory.MINUTES, DocumentCategory.OTHER -> ""
    }

    companion object {
        const val MAX_TITLE = 80
    }
}
