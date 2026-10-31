package com.example.condo_mini.ui.screens.doorman.parcels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.condo_mini.data.AppContainer
import com.example.condo_mini.domain.model.CondoUnit
import com.example.condo_mini.domain.model.Parcel
import com.example.condo_mini.domain.model.ParcelStatus
import com.example.condo_mini.domain.model.User
import com.example.condo_mini.domain.model.UserRole
import com.example.condo_mini.domain.repository.DirectoryRepository
import com.example.condo_mini.domain.repository.ParcelRepository
import com.example.condo_mini.domain.repository.SessionRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class DoormanFilter(val label: String) {
    WAITING("Aguardando"),
    DELIVERED("Entregues")
}

/** Opções rápidas do cadastro: a portaria escolhe com um toque em vez de digitar. */
val CARRIERS = listOf("Correios", "Mercado Livre", "Amazon", "Shopee", "iFood", "Outra")
val PACKAGE_TYPES = listOf("Envelope", "Caixa pequena", "Caixa média", "Caixa grande", "Sacola")

data class RegisterForm(
    val photoUrl: String? = null,
    val unit: String = "",
    val carrier: String = "",
    val description: String = "",
    val isSaving: Boolean = false,
    val error: String? = null
)

data class ParcelRow(val parcel: Parcel, val residents: List<String>)

data class DoormanParcelsUiState(
    val isLoading: Boolean = true,
    val filter: DoormanFilter = DoormanFilter.WAITING,
    val query: String = "",
    val waitingCount: Int = 0,
    val deliveredToday: Int = 0,
    val rows: List<ParcelRow> = emptyList(),
    val units: List<CondoUnit> = emptyList(),
    val residentsByUnit: Map<String, List<User>> = emptyMap(),
    val form: RegisterForm? = null,
    val delivering: ParcelRow? = null,
    val deliverError: String? = null,
    val isDelivering: Boolean = false,
    val userMessage: String? = null
)

private data class Ui(
    val filter: DoormanFilter = DoormanFilter.WAITING,
    val query: String = "",
    val form: RegisterForm? = null,
    val deliveringId: String? = null,
    val deliverError: String? = null,
    val isDelivering: Boolean = false,
    val userMessage: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class DoormanParcelsViewModel(
    private val parcelRepository: ParcelRepository = AppContainer.parcelRepository,
    private val directoryRepository: DirectoryRepository = AppContainer.directoryRepository,
    private val sessionRepository: SessionRepository = AppContainer.sessionRepository
) : ViewModel() {

    private val ui = MutableStateFlow(Ui())
    private val condoId get() = sessionRepository.currentUser.value?.condoId.orEmpty()

    /** "Porteiro José", como aparece para o morador. */
    private val doormanName: String
        get() = sessionRepository.currentUser.value?.name?.substringBefore(" ")
            ?.takeIf { it.isNotBlank() }?.let { "Porteiro $it" } ?: "Portaria"

    val uiState: StateFlow<DoormanParcelsUiState> = sessionRepository.currentUser
        .flatMapLatest { user ->
            val condo = user?.condoId.orEmpty()
            combine(
                parcelRepository.observeAllParcels(condo),
                directoryRepository.observeUnitsOf(condo),
                directoryRepository.observeUsersOf(condo),
                ui
            ) { parcels, units, users, ui -> build(parcels, units, users, ui) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DoormanParcelsUiState())

    private fun build(parcels: List<Parcel>, units: List<CondoUnit>, users: List<User>, ui: Ui): DoormanParcelsUiState {
        val residentsByUnit = users
            .filter { it.role == UserRole.RESIDENT || it.role == UserRole.MANAGER }
            .flatMap { user -> user.units.map { it to user } }
            .groupBy({ it.first }, { it.second })
        fun row(parcel: Parcel) = ParcelRow(parcel, residentsByUnit[parcel.unit].orEmpty().map { it.name })

        val today = LocalDate.now()
        val needle = ui.query.trim().lowercase()
        val visible = parcels
            .filter {
                when (ui.filter) {
                    DoormanFilter.WAITING -> it.status == ParcelStatus.WAITING
                    DoormanFilter.DELIVERED -> it.status == ParcelStatus.PICKED_UP
                }
            }
            // Código exato ou número do apartamento: é o que o morador fala no balcão.
            .filter { needle.isBlank() || it.pickupCode.startsWith(needle) || it.unit.lowercase().contains(needle) }
        return DoormanParcelsUiState(
            isLoading = false,
            filter = ui.filter,
            query = ui.query,
            waitingCount = parcels.count { it.status == ParcelStatus.WAITING },
            deliveredToday = parcels.count { it.pickedUpAt?.toLocalDate() == today },
            rows = visible.map(::row),
            units = units,
            residentsByUnit = residentsByUnit,
            form = ui.form,
            delivering = parcels.firstOrNull { it.id == ui.deliveringId }?.let(::row),
            deliverError = ui.deliverError,
            isDelivering = ui.isDelivering,
            userMessage = ui.userMessage
        )
    }

    fun onFilterSelected(filter: DoormanFilter) = ui.update { it.copy(filter = filter) }

    fun onQueryChanged(query: String) = ui.update { it.copy(query = query.take(30)) }

    fun onNewParcel() = ui.update { it.copy(form = RegisterForm()) }

    fun onDismissForm() = ui.update { it.copy(form = null) }

    fun onFormChanged(transform: (RegisterForm) -> RegisterForm) =
        ui.update { state -> state.copy(form = state.form?.let(transform)?.copy(error = null)) }

    fun onRegister() {
        val form = ui.value.form ?: return
        val error = when {
            form.unit.isBlank() -> "Escolha o apartamento."
            form.carrier.isBlank() -> "Escolha quem entregou."
            form.description.isBlank() -> "Escolha o tipo de volume."
            else -> null
        }
        if (error != null) {
            ui.update { state -> state.copy(form = state.form?.copy(error = error)) }
            return
        }
        ui.update { state -> state.copy(form = state.form?.copy(isSaving = true)) }
        viewModelScope.launch {
            val parcel = parcelRepository.register(
                condoId = condoId,
                unit = form.unit,
                description = form.description,
                carrier = form.carrier,
                receivedBy = doormanName,
                photoUrl = form.photoUrl
            )
            ui.update {
                it.copy(
                    form = null,
                    filter = DoormanFilter.WAITING,
                    userMessage = "Registrada para o ${parcel.unit}. Código ${parcel.pickupCode}."
                )
            }
        }
    }

    fun onDeliver(row: ParcelRow) = ui.update { it.copy(deliveringId = row.parcel.id, deliverError = null) }

    fun onDismissDeliver() = ui.update { it.copy(deliveringId = null, deliverError = null) }

    fun onConfirmDeliver(code: String) {
        val id = ui.value.deliveringId ?: return
        ui.update { it.copy(isDelivering = true, deliverError = null) }
        viewModelScope.launch {
            parcelRepository.deliver(id, code, doormanName)
                .onSuccess { parcel ->
                    ui.update {
                        it.copy(
                            deliveringId = null,
                            isDelivering = false,
                            query = "",
                            userMessage = "Entregue ao ${parcel.unit}."
                        )
                    }
                }
                .onFailure { error ->
                    ui.update { it.copy(isDelivering = false, deliverError = error.message) }
                }
        }
    }

    fun onMessageShown() = ui.update { it.copy(userMessage = null) }
}
