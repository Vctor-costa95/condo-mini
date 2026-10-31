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

    private val areas = MutableStateFlow(listOf(
        CommonArea(
            id = "a1",
            name = "Salão de festas",
            description = "Salão climatizado com cozinha equipada e banheiros.",
            capacity = 80,
            fee = 150.0,
            rules = "Limpeza por conta do morador. Som até 22h.",
            iconKey = "party",
            condoId = FakeDirectoryRepository.DEFAULT_CONDO_ID
        ),
        CommonArea(
            id = "a2",
            name = "Churrasqueira",
            description = "Área coberta com churrasqueira, pia e mesas.",
            capacity = 30,
            fee = 80.0,
            rules = "Trazer carvão. Devolver a área limpa.",
            iconKey = "grill",
            condoId = FakeDirectoryRepository.DEFAULT_CONDO_ID
        ),
        CommonArea(
            id = "a3",
            name = "Espaço gourmet",
            description = "Cozinha gourmet integrada com mesa para 12 pessoas.",
            capacity = 12,
            fee = 100.0,
            rules = "Proibido uso de som alto.",
            iconKey = "gourmet",
            condoId = FakeDirectoryRepository.DEFAULT_CONDO_ID
        ),
        CommonArea(
            id = "a4",
            name = "Quadra poliesportiva",
            description = "Quadra coberta para futebol, vôlei e basquete.",
            capacity = 20,
            fee = 0.0,
            rules = "Uso de calçado adequado obrigatório.",
            iconKey = "court",
            condoId = FakeDirectoryRepository.DEFAULT_CONDO_ID
        ),
        CommonArea(
            id = "a5",
            name = "Sala de reuniões",
            description = "Sala com TV, mesa e 8 cadeiras.",
            capacity = 8,
            fee = 0.0,
            rules = "Reserva máxima de um período por dia.",
            iconKey = "meeting",
            condoId = FakeDirectoryRepository.DEFAULT_CONDO_ID
        ),
        CommonArea("a6", "Rooftop com piscina", "Piscina com borda infinita e vista para o mar.", 25, 0.0, "Uso até 22h. Crianças acompanhadas.", "pool", condoId = "c2"),
        CommonArea("a7", "Academia", "Equipamentos Technogym e sala de pilates.", 12, 0.0, "Uso de toalha obrigatório.", "gym", condoId = "c2"),
        CommonArea("a8", "Salão de festas", "Salão para 120 pessoas com copa.", 120, 250.0, "Som até 23h.", "party", condoId = "c3"),
        CommonArea("a9", "Quadra de tênis", "Quadra de saibro com iluminação.", 4, 0.0, "Reserva de um período por dia.", "tennis", condoId = "c3"),
        CommonArea("a10", "Brinquedoteca", "Espaço infantil com monitoria aos sábados.", 15, 0.0, "Crianças até 10 anos.", "kids", condoId = "c3")
    ))

    private val today = LocalDate.now()

    // Reservas de outras unidades: simulam indisponibilidade e alimentam a fila do síndico.
    private val reservations = MutableStateFlow(
        listOf(
            Reservation("r1", areas.value[0], today.plusDays(3), ReservationPeriod.NIGHT, "Apto 302 - Bloco B", ReservationStatus.CONFIRMED, "Marina Costa"),
            Reservation("r2", areas.value[1], today.plusDays(2), ReservationPeriod.AFTERNOON, "Apto 104 - Bloco A", ReservationStatus.CONFIRMED, "Paulo Mendes"),
            Reservation("r3", areas.value[0], today.plusDays(7), ReservationPeriod.AFTERNOON, "Apto 201 - Bloco C", ReservationStatus.PENDING, "Helena Rocha"),
            Reservation("r4", areas.value[2], today.plusDays(5), ReservationPeriod.NIGHT, "Apto 503 - Bloco B", ReservationStatus.PENDING, "Ricardo Alves")
        )
    )

    override fun observeAreas(condoId: String): Flow<List<CommonArea>> =
        areas.map { list -> list.filter { it.condoId == condoId } }

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
        val area = areas.value.firstOrNull { it.id == areaId }
            ?: return Result.failure(IllegalArgumentException("Área não encontrada."))
        area.closedReason?.let {
            return Result.failure(IllegalStateException("Espaço interditado: $it"))
        }
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

    override suspend fun saveArea(area: CommonArea): CommonArea {
        delay(250)
        val saved = if (area.id.isBlank()) area.copy(id = UUID.randomUUID().toString()) else area
        areas.update { list ->
            if (list.any { it.id == saved.id }) list.map { if (it.id == saved.id) saved else it } else list + saved
        }
        // Reservas guardam uma cópia do espaço: mantém nome e ícone em dia.
        reservations.update { list -> list.map { if (it.area.id == saved.id) it.copy(area = saved) else it } }
        return saved
    }

    override suspend fun deleteArea(areaId: String) {
        delay(250)
        areas.update { list -> list.filterNot { it.id == areaId } }
        reservations.update { list ->
            list.map {
                if (it.area.id == areaId && !it.date.isBefore(today) && it.status.holdsSlot()) {
                    it.copy(status = ReservationStatus.CANCELED)
                } else {
                    it
                }
            }
        }
    }

    override suspend fun setAreaClosed(areaId: String, reason: String?) {
        val normalized = reason?.trim()?.ifBlank { "Em manutenção" }
        areas.update { list -> list.map { if (it.id == areaId) it.copy(closedReason = normalized) else it } }
        reservations.update { list ->
            list.map { if (it.area.id == areaId) it.copy(area = it.area.copy(closedReason = normalized)) else it }
        }
    }

    private fun updateReservation(id: String, transform: (Reservation) -> Reservation) {
        reservations.update { list -> list.map { if (it.id == id) transform(it) else it } }
    }

    // Recusadas e canceladas liberam o horário.
    private fun ReservationStatus.holdsSlot() =
        this == ReservationStatus.CONFIRMED || this == ReservationStatus.PENDING
}
