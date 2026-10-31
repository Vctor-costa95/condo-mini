package com.example.condo_mini.ui.screens.admin.areas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.condo_mini.data.AppContainer
import com.example.condo_mini.domain.model.CommonArea
import com.example.condo_mini.domain.model.ReservationStatus
import com.example.condo_mini.domain.repository.DirectoryRepository
import com.example.condo_mini.domain.repository.ReservationRepository
import com.example.condo_mini.ui.components.AreaIcon
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class AreaForm(
    /** Vazio para um espaço novo. */
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val capacity: String = "",
    val fee: String = "",
    val rules: String = "",
    val iconKey: String = AreaIcon.PARTY.key,
    val closedReason: String? = null,
    val isSaving: Boolean = false,
    val error: String? = null
) {
    val isNew: Boolean get() = id.isBlank()

    /** Como o espaço vai aparecer para o morador, já com o que foi digitado. */
    fun preview(): CommonArea = CommonArea(
        id = id,
        name = name.trim(),
        description = description.trim(),
        capacity = capacity.toIntOrNull() ?: 0,
        fee = fee.toFeeOrNull() ?: 0.0,
        rules = rules.trim(),
        iconKey = iconKey,
        closedReason = closedReason
    )
}

data class AdminAreasUiState(
    val isLoading: Boolean = true,
    val areas: List<CommonArea> = emptyList(),
    /** Reservas confirmadas ou pendentes de hoje em diante, por espaço. */
    val upcoming: Map<String, Int> = emptyMap(),
    val form: AreaForm? = null,
    val userMessage: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class AdminAreasViewModel(
    private val reservationRepository: ReservationRepository = AppContainer.reservationRepository,
    private val directoryRepository: DirectoryRepository = AppContainer.directoryRepository
) : ViewModel() {

    private val form = MutableStateFlow<AreaForm?>(null)
    private val userMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<AdminAreasUiState> = combine(
        directoryRepository.selectedCondoId.flatMapLatest { reservationRepository.observeAreas(it) },
        reservationRepository.observeAllReservations(),
        form,
        userMessage
    ) { areas, reservations, form, message ->
        val today = LocalDate.now()
        AdminAreasUiState(
            isLoading = false,
            areas = areas,
            upcoming = reservations
                .filter {
                    !it.date.isBefore(today) &&
                        (it.status == ReservationStatus.CONFIRMED || it.status == ReservationStatus.PENDING)
                }
                .groupingBy { it.area.id }
                .eachCount(),
            // Mantém a interdição em dia se ela mudar com a ficha aberta.
            form = form?.let { f -> f.copy(closedReason = areas.firstOrNull { it.id == f.id }?.closedReason) },
            userMessage = message
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AdminAreasUiState())

    fun onNewArea() {
        form.value = AreaForm()
    }

    fun onEditArea(area: CommonArea) {
        form.value = AreaForm(
            id = area.id,
            name = area.name,
            description = area.description,
            capacity = area.capacity.toString(),
            fee = if (area.fee > 0) "%.2f".format(area.fee).replace('.', ',') else "",
            rules = area.rules,
            iconKey = area.iconKey.ifBlank { AreaIcon.MEETING.key },
            closedReason = area.closedReason
        )
    }

    fun onDismissForm() {
        form.value = null
    }

    fun onFormChanged(transform: (AreaForm) -> AreaForm) {
        form.update { it?.let(transform)?.copy(error = null) }
    }

    fun onSave() {
        val current = form.value ?: return
        val error = when {
            current.name.isBlank() -> "Dê um nome ao espaço."
            (current.capacity.toIntOrNull() ?: 0) <= 0 -> "Informe quantas pessoas cabem no espaço."
            current.fee.isNotBlank() && current.fee.toFeeOrNull() == null -> "Taxa inválida. Use, por exemplo, 120,00."
            else -> null
        }
        if (error != null) {
            form.update { it?.copy(error = error) }
            return
        }
        form.update { it?.copy(isSaving = true) }
        viewModelScope.launch {
            val saved = reservationRepository.saveArea(
                current.preview().copy(condoId = directoryRepository.selectedCondoId.value)
            )
            form.value = null
            userMessage.value = if (current.isNew) "Espaço criado: ${saved.name}. Já disponível para reserva." else "Alterações salvas em ${saved.name}."
        }
    }

    fun onDelete() {
        val current = form.value ?: return
        viewModelScope.launch {
            reservationRepository.deleteArea(current.id)
            form.value = null
            userMessage.value = "Espaço excluído: ${current.name}."
        }
    }

    fun onCloseArea(area: CommonArea, reason: String) {
        viewModelScope.launch {
            reservationRepository.setAreaClosed(area.id, reason)
            userMessage.value = "Espaço interditado: ${area.name}. Novas reservas estão bloqueadas."
        }
    }

    fun onReopenArea(area: CommonArea) {
        viewModelScope.launch {
            reservationRepository.setAreaClosed(area.id, null)
            userMessage.value = "Espaço liberado: ${area.name}."
        }
    }

    fun onMessageShown() {
        userMessage.value = null
    }
}

/** Aceita "120", "120,50" e "120.50". */
private fun String.toFeeOrNull(): Double? = trim().replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0 }
