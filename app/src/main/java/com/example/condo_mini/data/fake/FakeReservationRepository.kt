package com.example.condo_mini.data.fake

import com.example.condo_mini.domain.model.CommonArea
import com.example.condo_mini.domain.model.Reservation
import com.example.condo_mini.domain.model.ReservationPeriod
import com.example.condo_mini.domain.model.ReservationStatus
import com.example.condo_mini.domain.repository.ReservationRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.util.UUID

class FakeReservationRepository : ReservationRepository {

    private val areas = listOf(
        CommonArea(
            id = "a1",
            name = "Salão de festas",
            description = "Salão climatizado com cozinha equipada e banheiros.",
            capacity = 80,
            fee = 150.0,
            rules = "Limpeza por conta do morador. Som até 22h."
        ),
        CommonArea(
            id = "a2",
            name = "Churrasqueira",
            description = "Área coberta com churrasqueira, pia e mesas.",
            capacity = 30,
            fee = 80.0,
            rules = "Trazer carvão. Devolver a área limpa."
        ),
        CommonArea(
            id = "a3",
            name = "Espaço gourmet",
            description = "Cozinha gourmet integrada com mesa para 12 pessoas.",
            capacity = 12,
            fee = 100.0,
            rules = "Proibido uso de som alto."
        ),
        CommonArea(
            id = "a4",
            name = "Quadra poliesportiva",
            description = "Quadra coberta para futebol, vôlei e basquete.",
            capacity = 20,
            fee = 0.0,
            rules = "Uso de calçado adequado obrigatório."
        ),
        CommonArea(
            id = "a5",
            name = "Sala de reuniões",
            description = "Sala com TV, mesa e 8 cadeiras.",
            capacity = 8,
            fee = 0.0,
            rules = "Reserva máxima de um período por dia."
        )
    )

    private val today = LocalDate.now()

    // Reservas de outras unidades: simulam indisponibilidade e alimentam a fila do síndico.
    private val reservations = MutableStateFlow(
        listOf(
            Reservation("r1", areas[0], today.plusDays(3), ReservationPeriod.NIGHT, "Apto 302 - Bloco B", ReservationStatus.CONFIRMED, "Marina Costa"),
            Reservation("r2", areas[1], today.plusDays(2), ReservationPeriod.AFTERNOON, "Apto 104 - Bloco A", ReservationStatus.CONFIRMED, "Paulo Mendes"),
            Reservation("r3", areas[0], today.plusDays(7), ReservationPeriod.AFTERNOON, "Apto 201 - Bloco C", ReservationStatus.PENDING, "Helena Rocha"),
            Reservation("r4", areas[2], today.plusDays(5), ReservationPeriod.NIGHT, "Apto 503 - Bloco B", ReservationStatus.PENDING, "Ricardo Alves")
        )
    )

    override fun observeAreas(): Flow<List<CommonArea>> = MutableStateFlow(areas)

    override fun observeReservations(unit: String): Flow<List<Reservation>> =
        reservations.map { list ->
            list.filter { it.unit == unit && it.status != ReservationStatus.CANCELED }
                .sortedBy { it.date }
        }

    override fun observeAllReservations(): Flow<List<Reservation>> =
        reservations.map { list ->
            list.filter { it.status != ReservationStatus.CANCELED }.sortedBy { it.date }
        }

    override suspend fun bookedPeriods(areaId: String, date: LocalDate): Set<ReservationPeriod> =
        reservations.value
            .filter { it.area.id == areaId && it.date == date && it.status.holdsSlot() }
            .map { it.period }
            .toSet()

    override suspend fun reserve(
        areaId: String,
        date: LocalDate,
        period: ReservationPeriod,
        unit: String,
        residentName: String
    ): Result<Reservation> {
        delay(400)
        val area = areas.firstOrNull { it.id == areaId }
            ?: return Result.failure(IllegalArgumentException("Área não encontrada."))
        if (date.isBefore(today)) {
            return Result.failure(IllegalArgumentException("Escolha uma data a partir de hoje."))
        }
        if (period in bookedPeriods(areaId, date)) {
            return Result.failure(IllegalStateException("Este período já está reservado."))
        }
        val reservation = Reservation(
            id = UUID.randomUUID().toString(),
            area = area,
            date = date,
            period = period,
            unit = unit,
            // Áreas com taxa dependem de aprovação do síndico.
            status = if (area.fee > 0) ReservationStatus.PENDING else ReservationStatus.CONFIRMED,
            residentName = residentName
        )
        reservations.update { it + reservation }
        return Result.success(reservation)
    }

    override suspend fun cancel(reservationId: String) {
        updateReservation(reservationId) { it.copy(status = ReservationStatus.CANCELED) }
    }

    override suspend fun approve(reservationId: String) {
        delay(300)
        updateReservation(reservationId) { it.copy(status = ReservationStatus.CONFIRMED) }
    }

    override suspend fun reject(reservationId: String, reason: String) {
        delay(300)
        updateReservation(reservationId) {
            it.copy(status = ReservationStatus.REJECTED, rejectionReason = reason.trim().ifBlank { null })
        }
    }

    private fun updateReservation(id: String, transform: (Reservation) -> Reservation) {
        reservations.update { list -> list.map { if (it.id == id) transform(it) else it } }
    }

    // Recusadas e canceladas liberam o horário.
    private fun ReservationStatus.holdsSlot() =
        this == ReservationStatus.CONFIRMED || this == ReservationStatus.PENDING
}
