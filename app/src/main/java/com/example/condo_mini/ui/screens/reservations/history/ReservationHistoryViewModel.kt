package com.example.condo_mini.ui.screens.reservations.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.condo_mini.data.AppContainer
import com.example.condo_mini.domain.model.Reservation
import com.example.condo_mini.domain.model.ReservationStatus
import com.example.condo_mini.domain.model.ServiceBooking
import com.example.condo_mini.domain.model.UserRole
import com.example.condo_mini.domain.repository.ReservationRepository
import com.example.condo_mini.domain.repository.ServiceBookingRepository
import com.example.condo_mini.domain.repository.SessionRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth

/** Reserva de espaço, mudança ou reparo, do jeito que o Histórico mostra. */
data class HistoryEntry(
    val id: String,
    val date: LocalDate,
    val title: String,
    val timeLabel: String,
    val unit: String,
    val residentName: String,
    val status: ReservationStatus,
    val reason: String?,
    /** Para ordenar itens do mesmo dia: hora de início. */
    val startHour: Int
)

enum class HistoryFilter(val label: String, val statuses: Set<ReservationStatus>?) {
    ALL("Todos", null),
    CONFIRMED("Confirmadas", setOf(ReservationStatus.CONFIRMED)),
    CANCELED("Canceladas", setOf(ReservationStatus.CANCELED)),
    REJECTED("Recusadas", setOf(ReservationStatus.REJECTED)),
    PENDING("Aguardando", setOf(ReservationStatus.PENDING))
}

data class ReservationHistoryUiState(
    val isLoading: Boolean = true,
    /** Síndico vê o condomínio todo e pode filtrar por apartamento. */
    val isManager: Boolean = false,
    val filter: HistoryFilter = HistoryFilter.ALL,
    val unitQuery: String = "",
    /** Mês a mês, do mais recente para o mais antigo. */
    val months: List<Pair<YearMonth, List<HistoryEntry>>> = emptyList(),
    val totalCount: Int = 0
)

@OptIn(ExperimentalCoroutinesApi::class)
class ReservationHistoryViewModel(
    private val reservationRepository: ReservationRepository = AppContainer.reservationRepository,
    private val serviceBookingRepository: ServiceBookingRepository = AppContainer.serviceBookingRepository,
    sessionRepository: SessionRepository = AppContainer.sessionRepository
) : ViewModel() {

    private val filter = MutableStateFlow(HistoryFilter.ALL)
    private val unitQuery = MutableStateFlow("")

    private val entries = sessionRepository.currentUser.flatMapLatest { user ->
        if (user == null) return@flatMapLatest flowOf(null)
        val manager = user.role == UserRole.MANAGER || user.role == UserRole.ADMIN
        // Síndico: condomínio todo. Morador: só o apartamento que está vendo agora.
        val unit = if (manager) null else user.unit
        combine(
            reservationRepository.observeReservationHistory(unit),
            serviceBookingRepository.observeBookingHistory(user.condoId, unit)
        ) { reservations, services ->
            manager to (reservations.map { it.toEntry() } + services.map { it.toEntry() })
                .sortedWith(compareByDescending<HistoryEntry> { it.date }.thenByDescending { it.startHour })
        }
    }

    val uiState: StateFlow<ReservationHistoryUiState> = combine(entries, filter, unitQuery) { loaded, filter, query ->
        if (loaded == null) return@combine ReservationHistoryUiState()
        val (manager, all) = loaded
        val needle = query.trim().lowercase()
        val visible = all.filter { entry ->
            (filter.statuses == null || entry.status in filter.statuses) &&
                (needle.isEmpty() || entry.unit.lowercase().contains(needle))
        }
        ReservationHistoryUiState(
            isLoading = false,
            isManager = manager,
            filter = filter,
            unitQuery = query,
            months = visible.groupBy { YearMonth.from(it.date) }.toList(),
            totalCount = visible.size
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReservationHistoryUiState())

    fun onFilterSelected(selected: HistoryFilter) {
        filter.value = selected
    }

    fun onUnitQueryChanged(query: String) {
        unitQuery.value = query.take(40)
    }
}

private fun Reservation.toEntry() = HistoryEntry(
    id = "r-$id",
    date = date,
    title = area.name,
    timeLabel = timeLabel,
    unit = unit,
    residentName = residentName,
    status = status,
    reason = rejectionReason,
    startHour = startHour
)

private fun ServiceBooking.toEntry() = HistoryEntry(
    id = "s-$id",
    date = date,
    title = title,
    timeLabel = listOfNotNull("${period.label} · ${period.hours}", provider.ifBlank { null }).joinToString(" · "),
    unit = unit,
    residentName = residentName,
    status = status,
    reason = rejectionReason,
    startHour = period.startHour
)
