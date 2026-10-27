package com.example.condo_mini.ui.screens.occurrences

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.condo_mini.data.AppContainer
import com.example.condo_mini.domain.model.Occurrence
import com.example.condo_mini.domain.model.OccurrenceCategory
import com.example.condo_mini.domain.repository.OccurrenceRepository
import com.example.condo_mini.domain.repository.SessionRepository
import com.example.condo_mini.ui.components.MAX_ATTACHED_PHOTOS
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OccurrenceForm(
    val category: OccurrenceCategory? = null,
    val title: String = "",
    val description: String = "",
    val photoUrls: List<String> = emptyList(),
    val isSubmitting: Boolean = false,
    val error: String? = null
)

data class OccurrencesUiState(
    val isLoading: Boolean = true,
    val occurrences: List<Occurrence> = emptyList(),
    val form: OccurrenceForm? = null,
    val userMessage: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class OccurrencesViewModel(
    private val occurrenceRepository: OccurrenceRepository = AppContainer.occurrenceRepository,
    private val sessionRepository: SessionRepository = AppContainer.sessionRepository
) : ViewModel() {

    private val form = MutableStateFlow<OccurrenceForm?>(null)
    private val userMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<OccurrencesUiState> = combine(
        sessionRepository.currentUser.flatMapLatest { user ->
            occurrenceRepository.observeOccurrences(user?.unit.orEmpty())
        },
        form,
        userMessage
    ) { occurrences, form, message ->
        OccurrencesUiState(
            isLoading = false,
            occurrences = occurrences,
            form = form,
            userMessage = message
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OccurrencesUiState())

    fun onNewOccurrence() {
        form.value = OccurrenceForm()
    }

    fun onDismissForm() {
        form.value = null
    }

    fun onCategoryChanged(category: OccurrenceCategory) {
        form.update { it?.copy(category = category, error = null) }
    }

    fun onTitleChanged(title: String) {
        form.update { it?.copy(title = title.take(MAX_TITLE), error = null) }
    }

    fun onDescriptionChanged(description: String) {
        form.update { it?.copy(description = description.take(MAX_DESCRIPTION), error = null) }
    }

    fun onPhotoAdded(url: String) {
        form.update { it?.copy(photoUrls = (it.photoUrls + url).take(MAX_ATTACHED_PHOTOS)) }
    }

    fun onPhotoRemoved(url: String) {
        form.update { it?.copy(photoUrls = it.photoUrls - url) }
    }

    fun onSubmit() {
        val current = form.value ?: return
        val error = when {
            current.category == null -> "Selecione uma categoria."
            current.title.isBlank() -> "Informe um título."
            current.description.trim().length < MIN_DESCRIPTION ->
                "Descreva a ocorrência com pelo menos $MIN_DESCRIPTION caracteres."
            else -> null
        }
        if (error != null) {
            form.update { it?.copy(error = error) }
            return
        }
        val category = requireNotNull(current.category)
        form.update { it?.copy(isSubmitting = true) }
        viewModelScope.launch {
            val user = sessionRepository.currentUser.value
            val occurrence = occurrenceRepository.register(
                title = current.title,
                description = current.description,
                category = category,
                unit = user?.unit.orEmpty(),
                authorName = user?.name.orEmpty(),
                photoUrls = current.photoUrls
            )
            form.value = null
            userMessage.value = "Ocorrência registrada. Protocolo ${occurrence.protocol}"
        }
    }

    /** Complemento do morador quando a administração pede mais detalhes. */
    fun onReply(occurrence: Occurrence, text: String, photoUrls: List<String>) {
        if (text.isBlank() && photoUrls.isEmpty()) return
        viewModelScope.launch {
            occurrenceRepository.replyAsResident(
                occurrenceId = occurrence.id,
                text = text,
                author = sessionRepository.currentUser.value?.name.orEmpty(),
                photoUrls = photoUrls
            )
            userMessage.value = "Detalhes enviados à administração."
        }
    }

    fun onMessageShown() {
        userMessage.value = null
    }

    companion object {
        const val MAX_TITLE = 60
        const val MAX_DESCRIPTION = 500
        const val MIN_DESCRIPTION = 10
    }
}
