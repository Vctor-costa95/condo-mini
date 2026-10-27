package com.example.condo_mini.ui.screens.manager.occurrences

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.condo_mini.data.AppContainer
import com.example.condo_mini.domain.model.Occurrence
import com.example.condo_mini.domain.model.OccurrenceStatus
import com.example.condo_mini.domain.repository.OccurrenceRepository
import com.example.condo_mini.domain.repository.SessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class OccurrenceFilter(val label: String, val statuses: Set<OccurrenceStatus>) {
    TO_HANDLE("A tratar", setOf(OccurrenceStatus.OPEN, OccurrenceStatus.IN_REVIEW)),
    WAITING_RESIDENT("Aguardando morador", setOf(OccurrenceStatus.NEEDS_INFO)),
    RESOLVED("Resolvidas", setOf(OccurrenceStatus.RESOLVED))
}

enum class ManagerAction(val label: String, val placeholder: String) {
    RESPOND("Responder", "Escreva uma resposta ao morador…"),
    REQUEST_DETAILS("Pedir detalhes", "O que você precisa saber?"),
    RESOLVE("Resolver", "Mensagem de encerramento…")
}

data class ManagerOccurrencesUiState(
    val isLoading: Boolean = true,
    val filter: OccurrenceFilter = OccurrenceFilter.TO_HANDLE,
    val counts: Map<OccurrenceFilter, Int> = emptyMap(),
    val occurrences: List<Occurrence> = emptyList(),
    val selected: Occurrence? = null,
    val isSending: Boolean = false,
    val userMessage: String? = null
)

class ManagerOccurrencesViewModel(
    private val occurrenceRepository: OccurrenceRepository = AppContainer.occurrenceRepository,
    private val sessionRepository: SessionRepository = AppContainer.sessionRepository
) : ViewModel() {

    private val filter = MutableStateFlow(OccurrenceFilter.TO_HANDLE)
    private val selectedId = MutableStateFlow<String?>(null)
    private val isSending = MutableStateFlow(false)
    private val userMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<ManagerOccurrencesUiState> = combine(
        occurrenceRepository.observeAllOccurrences(),
        filter,
        selectedId,
        isSending,
        userMessage
    ) { all, filter, selectedId, sending, message ->
        ManagerOccurrencesUiState(
            isLoading = false,
            filter = filter,
            counts = OccurrenceFilter.entries.associateWith { f -> all.count { it.status in f.statuses } },
            occurrences = all.filter { it.status in filter.statuses },
            // Busca na lista completa: a ocorrência continua aberta mesmo se mudar de filtro.
            selected = all.firstOrNull { it.id == selectedId },
            isSending = sending,
            userMessage = message
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ManagerOccurrencesUiState())

    fun onFilterSelected(value: OccurrenceFilter) {
        filter.value = value
    }

    fun onOpen(occurrence: Occurrence) {
        selectedId.value = occurrence.id
    }

    fun onClose() {
        selectedId.value = null
    }

    fun onSend(action: ManagerAction, text: String, photoUrls: List<String>) {
        val occurrence = uiState.value.selected ?: return
        if (text.isBlank() && photoUrls.isEmpty()) return
        val author = sessionRepository.currentUser.value?.name
            ?.takeIf { it.isNotBlank() }?.let { "Síndico - $it" } ?: "Síndico"
        isSending.value = true
        viewModelScope.launch {
            when (action) {
                ManagerAction.RESPOND ->
                    occurrenceRepository.respond(occurrence.id, text, author, resolve = false, photoUrls = photoUrls)
                ManagerAction.RESOLVE ->
                    occurrenceRepository.respond(occurrence.id, text, author, resolve = true, photoUrls = photoUrls)
                ManagerAction.REQUEST_DETAILS ->
                    occurrenceRepository.requestDetails(occurrence.id, text, author, photoUrls)
            }
            isSending.value = false
            userMessage.value = when (action) {
                ManagerAction.RESPOND -> "Resposta enviada a ${occurrence.unit}."
                ManagerAction.REQUEST_DETAILS -> "Pedido de detalhes enviado a ${occurrence.unit}."
                ManagerAction.RESOLVE -> "${occurrence.protocol} marcada como resolvida."
            }
            if (action == ManagerAction.RESOLVE) selectedId.value = null
        }
    }

    fun onMessageShown() {
        userMessage.value = null
    }
}
