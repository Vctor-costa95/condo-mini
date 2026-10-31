package com.example.condo_mini.ui.screens.manager.reservations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.condo_mini.data.AppContainer
import com.example.condo_mini.domain.model.Reservation
import com.example.condo_mini.domain.model.ReservationStatus
import com.example.condo_mini.domain.model.ServiceBooking
import com.example.condo_mini.domain.repository.ReservationRepository
import com.example.condo_mini.domain.repository.ServiceBookingRepository
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

data class ManagerReservationsUiState(
    val isLoading: Boolean = true,
    val pending: List<Reservation> = emptyList(),
    val upcoming: List<Reservation> = emptyList(),
    val pendingServices: List<ServiceBooking> = emptyList(),
    val upcomingServices: List<ServiceBooking> = emptyList(),
    val processingIds: Set<String> = emptySet(),
    val userMessage: String? = null
) {
    val pendingCount: Int get() = pending.size + pendingServices.size
}

@OptIn(ExperimentalCoroutinesApi::class)
class ManagerReservationsViewModel(
    private val reservationRepository: ReservationRepository = AppContainer.reservationRepository,
    private val serviceBookingRepository: ServiceBookingRepository = AppContainer.serviceBookingRepository,
    sessionRepository: SessionRepository = AppContainer.sessionRepository
) : ViewModel() {

    private val processingIds = MutableStateFlow<Set<String>>(emptySet())
    private val userMessage = MutableStateFlow<String?>(null)

    private val services = sessionRepository.currentUser.flatMapLatest { user ->
        serviceBookingRepository.observeAllBookings(user?.condoId.orEmpty())
    }

    val uiState: StateFlow<ManagerReservationsUiState> = combine(
        reservationRepository.observeAllReservations(),
        services,
        processingIds,
        userMessage
    ) { reservations, services, processing, message ->
        val today = LocalDate.now()
        ManagerReservationsUiState(
            isLoading = false,
            pending = reservations.filter { it.status == ReservationStatus.PENDING },
            upcoming = reservations.filter { it.status == ReservationStatus.CONFIRMED && !it.date.isBefore(today) },
            pendingServices = services.filter { it.status == ReservationStatus.PENDING },
            upcomingServices = services.filter { it.status == ReservationStatus.CONFIRMED && !it.date.isBefore(today) },
            processingIds = processing,
            userMessage = message
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ManagerReservationsUiState())

    fun onApprove(reservation: Reservation) = process(reservation.id) {
        reservationRepository.approve(reservation.id)
        "Reserva de ${reservation.unit} aprovada."
    }

    fun onReject(reservation: Reservation, reason: String) = process(reservation.id) {
        reservationRepository.reject(reservation.id, reason)
        "Reserva de ${reservation.unit} recusada."
    }

    fun onApproveService(booking: ServiceBooking) = process(booking.id) {
        serviceBookingRepository.approve(booking.id).fold(
            onSuccess = { "Pedido do ${booking.unit} aprovado." },
            onFailure = { it.message ?: "Não foi possível aprovar." }
        )
    }

    fun onRejectService(booking: ServiceBooking, reason: String) = process(booking.id) {
        serviceBookingRepository.reject(booking.id, reason)
        "Pedido do ${booking.unit} recusado."
    }

    fun onMessageShown() {
        userMessage.value = null
    }

    private fun process(id: String, action: suspend () -> String) {
        processingIds.update { it + id }
        viewModelScope.launch {
            userMessage.value = action()
            processingIds.update { it - id }
        }
    }
}
