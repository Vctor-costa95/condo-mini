package com.example.condo_mini.ui.screens.reservations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.condo_mini.data.AppContainer
import com.example.condo_mini.domain.model.CommonArea
import com.example.condo_mini.domain.model.Reservation
import com.example.condo_mini.domain.model.ReservationPeriod
import com.example.condo_mini.domain.model.ReservationStatus
import com.example.condo_mini.domain.model.RepairKind
import com.example.condo_mini.domain.model.ServiceBooking
import com.example.condo_mini.domain.model.ServiceType
import com.example.condo_mini.domain.repository.ReservationRepository
import com.example.condo_mini.domain.repository.SessionRepository
import com.example.condo_mini.domain.repository.ServiceBookingRepository
import com.example.condo_mini.ui.util.formatDate
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

/** Estado do bottom sheet de mudança ou reparo. */
data class ServiceRequestState(
    val type: ServiceType,
    val date: LocalDate = LocalDate.now().plusDays(1),
    val takenPeriods: Set<ReservationPeriod> = emptySet(),
    val period: ReservationPeriod? = null,
    val repairKind: RepairKind? = null,
    val provider: String = "",
    val isSubmitting: Boolean = false,
    val error: String? = null
)

data class ReservationsUiState(
    val isLoading: Boolean = true,
    val areas: List<CommonArea> = emptyList(),
    val myReservations: List<Reservation> = emptyList(),
    val booking: BookingState? = null,
    val myServices: List<ServiceBooking> = emptyList(),
    val serviceRequest: ServiceRequestState? = null,
    val userMessage: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class ReservationsViewModel(
    private val reservationRepository: ReservationRepository = AppContainer.reservationRepository,
    private val sessionRepository: SessionRepository = AppContainer.sessionRepository,
    private val serviceBookingRepository: ServiceBookingRepository = AppContainer.serviceBookingRepository
) : ViewModel() {

    private val booking = MutableStateFlow<BookingState?>(null)
    private val serviceRequest = MutableStateFlow<ServiceRequestState?>(null)
    private val userMessage = MutableStateFlow<String?>(null)

    private val myReservations = sessionRepository.currentUser.flatMapLatest { user ->
        reservationRepository.observeReservations(user?.unit.orEmpty())
    }

    private val myServices = sessionRepository.currentUser.flatMapLatest { user ->
        serviceBookingRepository.observeBookings(user?.condoId.orEmpty(), user?.unit.orEmpty())
    }

    private val sheets = combine(booking, serviceRequest) { booking, service -> booking to service }

    val uiState: StateFlow<ReservationsUiState> = combine(
        sessionRepository.currentUser.flatMapLatest { user ->
            reservationRepository.observeAreas(user?.condoId.orEmpty())
        },
        myReservations,
        myServices,
        sheets,
        userMessage
    ) { areas, reservations, services, (booking, service), message ->
        ReservationsUiState(
            isLoading = false,
            areas = areas,
            myReservations = reservations,
            booking = booking,
            myServices = services,
            serviceRequest = service,
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

    fun onNewService(type: ServiceType) {
        serviceRequest.value = ServiceRequestState(type = type)
        refreshTakenPeriods()
    }

    fun onServiceDateSelected(date: LocalDate) {
        serviceRequest.update { it?.copy(date = date, period = null, error = null) }
        refreshTakenPeriods()
    }

    fun onServiceChanged(transform: (ServiceRequestState) -> ServiceRequestState) {
        serviceRequest.update { state -> state?.let(transform)?.copy(error = null) }
    }

    fun onDismissService() {
        serviceRequest.value = null
    }

    fun onConfirmService() {
        val current = serviceRequest.value ?: return
        val error = when {
            current.period == null -> "Escolha manhã ou tarde."
            current.type == ServiceType.REPAIR && current.repairKind == null -> "Diga o que será feito."
            else -> null
        }
        if (error != null) {
            serviceRequest.update { it?.copy(error = error) }
            return
        }
        val user = sessionRepository.currentUser.value
        serviceRequest.update { it?.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            serviceBookingRepository.request(
                ServiceBooking(
                    id = "",
                    type = current.type,
                    date = current.date,
                    period = current.period!!,
                    unit = user?.unit.orEmpty(),
                    residentName = user?.name.orEmpty(),
                    condoId = user?.condoId.orEmpty(),
                    status = ReservationStatus.PENDING,
                    repairKind = current.repairKind.takeIf { current.type == ServiceType.REPAIR },
                    provider = current.provider
                )
            )
                .onSuccess { saved ->
                    serviceRequest.value = null
                    userMessage.value = when (saved.type) {
                        ServiceType.MOVE -> "Mudança enviada. Aguarde a aprovação do síndico."
                        ServiceType.REPAIR -> "Reparo enviado. Aguarde a aprovação do síndico."
                    }
                }
                .onFailure { error ->
                    serviceRequest.update { it?.copy(isSubmitting = false, error = error.message) }
                    refreshTakenPeriods()
                }
        }
    }

    fun onCancelService(booking: ServiceBooking) {
        viewModelScope.launch {
            serviceBookingRepository.cancel(booking.id)
            userMessage.value = "Agendamento de ${booking.date.formatDate()} cancelado."
        }
    }

    private fun refreshTakenPeriods() {
        val current = serviceRequest.value ?: return
        // Só mudança tem limite de horário; reparos podem acontecer em paralelo.
        if (current.type != ServiceType.MOVE) return
        val condoId = sessionRepository.currentUser.value?.condoId.orEmpty()
        viewModelScope.launch {
            val taken = serviceBookingRepository.takenMovePeriods(condoId, current.date)
            serviceRequest.update { state ->
                if (state?.date == current.date) state.copy(takenPeriods = taken) else state
            }
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
