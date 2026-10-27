package com.example.condo_mini.ui.screens.reservations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.condo_mini.data.AppContainer
import com.example.condo_mini.domain.model.CommonArea
import com.example.condo_mini.domain.model.Reservation
import com.example.condo_mini.domain.model.ReservationPeriod
import com.example.condo_mini.domain.model.ReservationStatus
import com.example.condo_mini.domain.repository.ReservationRepository
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

/** Estado do bottom sheet de nova reserva. */
data class BookingState(
    val area: CommonArea,
    val date: LocalDate = LocalDate.now(),
    val bookedPeriods: Set<ReservationPeriod> = emptySet(),
    val period: ReservationPeriod? = null,
    val isSubmitting: Boolean = false,
    val error: String? = null
)

data class ReservationsUiState(
    val isLoading: Boolean = true,
    val areas: List<CommonArea> = emptyList(),
    val myReservations: List<Reservation> = emptyList(),
    val booking: BookingState? = null,
    val userMessage: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class ReservationsViewModel(
    private val reservationRepository: ReservationRepository = AppContainer.reservationRepository,
    private val sessionRepository: SessionRepository = AppContainer.sessionRepository
) : ViewModel() {

    private val booking = MutableStateFlow<BookingState?>(null)
    private val userMessage = MutableStateFlow<String?>(null)

    private val myReservations = sessionRepository.currentUser.flatMapLatest { user ->
        reservationRepository.observeReservations(user?.unit.orEmpty())
    }

    val uiState: StateFlow<ReservationsUiState> = combine(
        reservationRepository.observeAreas(),
        myReservations,
        booking,
        userMessage
    ) { areas, reservations, booking, message ->
        ReservationsUiState(
            isLoading = false,
            areas = areas,
            myReservations = reservations,
            booking = booking,
            userMessage = message
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReservationsUiState())

    fun onAreaSelected(area: CommonArea) {
        booking.value = BookingState(area = area)
        refreshAvailability()
    }

    fun onDateSelected(date: LocalDate) {
        booking.update { it?.copy(date = date, period = null, error = null) }
        refreshAvailability()
    }

    fun onPeriodSelected(period: ReservationPeriod) {
        booking.update { it?.copy(period = period, error = null) }
    }

    fun onDismissBooking() {
        booking.value = null
    }

    fun onConfirmBooking() {
        val current = booking.value ?: return
        val period = current.period ?: run {
            booking.update { it?.copy(error = "Selecione um período.") }
            return
        }
        val user = sessionRepository.currentUser.value
        booking.update { it?.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            reservationRepository.reserve(
                areaId = current.area.id,
                date = current.date,
                period = period,
                unit = user?.unit.orEmpty(),
                residentName = user?.name.orEmpty()
            )
                .onSuccess { reservation ->
                    booking.value = null
                    userMessage.value = if (reservation.status == ReservationStatus.PENDING) {
                        "Reserva enviada! Aguarde a aprovação do síndico."
                    } else {
                        "Reserva confirmada!"
                    }
                }
                .onFailure { error ->
                    booking.update { it?.copy(isSubmitting = false, error = error.message) }
                    refreshAvailability()
                }
        }
    }

    fun onCancelReservation(reservation: Reservation) {
        viewModelScope.launch {
            reservationRepository.cancel(reservation.id)
            userMessage.value = "Reserva de ${reservation.area.name} cancelada."
        }
    }

    fun onMessageShown() {
        userMessage.value = null
    }

    private fun refreshAvailability() {
        val current = booking.value ?: return
        viewModelScope.launch {
            val booked = reservationRepository.bookedPeriods(current.area.id, current.date)
            booking.update { state ->
                if (state?.area?.id == current.area.id && state.date == current.date) {
                    state.copy(bookedPeriods = booked)
                } else {
                    state
                }
            }
        }
    }
}
