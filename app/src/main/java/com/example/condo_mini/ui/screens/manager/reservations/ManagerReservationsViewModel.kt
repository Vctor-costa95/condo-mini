package com.example.condo_mini.ui.screens.manager.reservations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.condo_mini.data.AppContainer
import com.example.condo_mini.domain.model.Reservation
import com.example.condo_mini.domain.model.ReservationStatus
import com.example.condo_mini.domain.repository.ReservationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class ManagerReservationsUiState(
    val isLoading: Boolean = true,
    val pending: List<Reservation> = emptyList(),
    val upcoming: List<Reservation> = emptyList(),
    val processingIds: Set<String> = emptySet(),
    val userMessage: String? = null
)

class ManagerReservationsViewModel(
    private val reservationRepository: ReservationRepository = AppContainer.reservationRepository
) : ViewModel() {

    private val processingIds = MutableStateFlow<Set<String>>(emptySet())
    private val userMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<ManagerReservationsUiState> = combine(
        reservationRepository.observeAllReservations(),
        processingIds,
        userMessage
    ) { reservations, processing, message ->
        val today = LocalDate.now()
        ManagerReservationsUiState(
            isLoading = false,
            pending = reservations.filter { it.status == ReservationStatus.PENDING },
            upcoming = reservations.filter { it.status == ReservationStatus.CONFIRMED && !it.date.isBefore(today) },
            processingIds = processing,
            userMessage = message
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ManagerReservationsUiState())

    fun onApprove(reservation: Reservation) = process(reservation) {
        reservationRepository.approve(reservation.id)
        "Reserva de ${reservation.unit} aprovada."
    }

    fun onReject(reservation: Reservation, reason: String) = process(reservation) {
        reservationRepository.reject(reservation.id, reason)
        "Reserva de ${reservation.unit} recusada."
    }

    fun onMessageShown() {
        userMessage.value = null
    }

    private fun process(reservation: Reservation, action: suspend () -> String) {
        processingIds.update { it + reservation.id }
        viewModelScope.launch {
            userMessage.value = action()
            processingIds.update { it - reservation.id }
        }
    }
}
