package com.example.condo_mini.ui.screens.documents

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.condo_mini.data.AppContainer
import com.example.condo_mini.domain.model.CondoDocument
import com.example.condo_mini.domain.repository.DocumentRepository
import com.example.condo_mini.domain.repository.SessionRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class DocumentsUiState(
    val isLoading: Boolean = true,
    val documents: List<CondoDocument> = emptyList()
)

/** Documentos do condomínio do usuário logado (somente leitura). */
@OptIn(ExperimentalCoroutinesApi::class)
class DocumentsViewModel(
    sessionRepository: SessionRepository = AppContainer.sessionRepository,
    private val documentRepository: DocumentRepository = AppContainer.documentRepository
) : ViewModel() {

    val uiState: StateFlow<DocumentsUiState> = sessionRepository.currentUser
        .flatMapLatest { user ->
            if (user == null) flowOf(emptyList()) else documentRepository.observeDocuments(user.condoId)
        }
        .map { DocumentsUiState(isLoading = false, documents = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DocumentsUiState())
}
