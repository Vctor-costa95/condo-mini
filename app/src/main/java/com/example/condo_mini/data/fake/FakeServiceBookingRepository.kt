package com.example.condo_mini.data.fake

import com.example.condo_mini.domain.model.RepairKind
import com.example.condo_mini.domain.model.ReservationPeriod
import com.example.condo_mini.domain.model.ReservationStatus
import com.example.condo_mini.domain.model.SERVICE_PERIODS
import com.example.condo_mini.domain.model.ServiceBooking
import com.example.condo_mini.domain.model.ServiceType
import com.example.condo_mini.domain.repository.ServiceBookingRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.util.UUID

class FakeServiceBookingRepository : ServiceBookingRepository {

    private val today = LocalDate.now()
    private val condo = FakeDirectoryRepository.DEFAULT_CONDO_ID

    // Pedidos de outras unidades: ocupam horários de mudança e alimentam a fila do síndico.
    private val bookings = MutableStateFlow(
        listOf(
            ServiceBooking("s1", ServiceType.MOVE, today.plusDays(2), ReservationPeriod.MORNING, "Apto 302 - Bloco B", "Marina Costa", condo, ReservationStatus.CONFIRMED),
            ServiceBooking("s2", ServiceType.MOVE, today.plusDays(4), ReservationPeriod.AFTERNOON, "Apto 1201 - Bloco A", "Fernando Dias", condo, ReservationStatus.PENDING, provider = "Mudanças Rápidas"),
            ServiceBooking("s3", ServiceType.REPAIR, today.plusDays(1), ReservationPeriod.AFTERNOON, "Apto 503 - Bloco B", "Ricardo Alves", condo, ReservationStatus.PENDING, RepairKind.INTERNET, "Vivo Fibra")
        )
    )

    override fun observeBookings(condoId: String, unit: String): Flow<List<ServiceBooking>> =
        bookings.map { list ->
            list.filter { it.condoId == condoId && it.unit == unit && it.status != ReservationStatus.CANCELED }
                .sortedBy { it.date }
        }

    override fun observeAllBookings(condoId: String): Flow<List<ServiceBooking>> =
        bookings.map { list ->
            list.filter { it.condoId == condoId && it.status != ReservationStatus.CANCELED }
                .sortedWith(compareBy({ it.date }, { it.period }))
        }

    override suspend fun takenMovePeriods(condoId: String, date: LocalDate): Set<ReservationPeriod> =
        bookings.value
            .filter { it.condoId == condoId && it.type == ServiceType.MOVE && it.date == date && it.status.holdsSlot() }
            .map { it.period }
            .toSet()

    override suspend fun request(booking: ServiceBooking): Result<ServiceBooking> {
        delay(400)
        if (booking.date.isBefore(today)) {
            return Result.failure(IllegalArgumentException("Escolha uma data a partir de hoje."))
        }
        if (booking.period !in SERVICE_PERIODS) {
            return Result.failure(IllegalArgumentException("Escolha manhã ou tarde."))
        }
        if (booking.type == ServiceType.MOVE && booking.period in takenMovePeriods(booking.condoId, booking.date)) {
            return Result.failure(IllegalStateException("Já existe uma mudança neste período."))
        }
        val saved = booking.copy(
            id = UUID.randomUUID().toString(),
            status = ReservationStatus.PENDING,
            provider = booking.provider.trim()
        )
        bookings.update { it + saved }
        return Result.success(saved)
    }

    override suspend fun cancel(bookingId: String) {
        update(bookingId) { it.copy(status = ReservationStatus.CANCELED) }
    }

    override suspend fun approve(bookingId: String): Result<ServiceBooking> {
        delay(300)
        val booking = bookings.value.firstOrNull { it.id == bookingId }
            ?: return Result.failure(IllegalArgumentException("Agendamento não encontrado."))
        // Duas mudanças pendentes não chegam ao mesmo horário, mas a regra fica garantida aqui também.
        val clash = booking.type == ServiceType.MOVE && bookings.value.any {
            it.id != booking.id && it.condoId == booking.condoId && it.type == ServiceType.MOVE &&
                it.date == booking.date && it.period == booking.period && it.status == ReservationStatus.CONFIRMED
        }
        if (clash) return Result.failure(IllegalStateException("Já há uma mudança aprovada neste período."))
        return Result.success(update(bookingId) { it.copy(status = ReservationStatus.CONFIRMED) })
    }

    override suspend fun reject(bookingId: String, reason: String) {
        delay(300)
        update(bookingId) {
            it.copy(status = ReservationStatus.REJECTED, rejectionReason = reason.trim().ifBlank { null })
        }
    }

    private fun update(id: String, transform: (ServiceBooking) -> ServiceBooking): ServiceBooking {
        bookings.update { list -> list.map { if (it.id == id) transform(it) else it } }
        return bookings.value.first { it.id == id }
    }

    // Recusados e cancelados liberam o horário.
    private fun ReservationStatus.holdsSlot() =
        this == ReservationStatus.CONFIRMED || this == ReservationStatus.PENDING
}
