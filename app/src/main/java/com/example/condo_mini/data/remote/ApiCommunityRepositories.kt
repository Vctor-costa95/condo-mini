package com.example.condo_mini.data.remote

import com.example.condo_mini.domain.model.CommonArea
import com.example.condo_mini.domain.model.Notice
import com.example.condo_mini.domain.model.NoticeCategory
import com.example.condo_mini.domain.model.Reservation
import com.example.condo_mini.domain.model.ReservationPeriod
import com.example.condo_mini.domain.model.ReservationSlot
import com.example.condo_mini.domain.model.ServiceBooking
import com.example.condo_mini.domain.repository.NoticeRepository
import com.example.condo_mini.domain.repository.ReservationRepository
import com.example.condo_mini.domain.repository.ServiceBookingRepository
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * Condomínio das listas que não recebem um: o do cadastro, ou o escolhido pelo
 * administrador (ver AppContainer).
 */
typealias CondoProvider = () -> String

class ApiNoticeRepository(private val api: ApiClient, private val condoId: CondoProvider) : NoticeRepository {

    override fun observeNotices(): Flow<List<Notice>> =
        api.live { api.get("notices", mapOf("condoId" to condoId())).list { it.toNotice() } }

    // O servidor assina com o nome de quem está logado; [author] fica para os dados de demonstração.
    override suspend fun publish(
        title: String,
        body: String,
        category: NoticeCategory,
        pinned: Boolean,
        author: String
    ): Notice = api.send(
        "POST", "notices",
        JSONObject()
            .put("condoId", condoId())
            .put("title", title.trim())
            .put("body", body.trim())
            .put("category", category.name)
            .put("pinned", pinned)
    ).obj { it.toNotice() }

    override suspend fun delete(noticeId: String) = api.quietly {
        api.send("DELETE", "notices/$noticeId")
    }
}

class ApiReservationRepository(private val api: ApiClient, private val condoId: CondoProvider) : ReservationRepository {

    override fun observeAreas(condoId: String): Flow<List<CommonArea>> =
        api.live { api.get("areas", mapOf("condoId" to condoId)).list { it.toArea() } }

    override fun observeReservations(unit: String): Flow<List<Reservation>> =
        api.live { api.get("reservations", mapOf("condoId" to condoId(), "unit" to unit)).list { it.toReservation() } }

    override fun observeAllReservations(): Flow<List<Reservation>> =
        api.live { api.get("reservations", mapOf("condoId" to condoId())).list { it.toReservation() } }

    override suspend fun bookedPeriods(areaId: String, date: LocalDate): Set<ReservationPeriod> =
        runCatching {
            JSONArray(api.get("areas/$areaId/booked", mapOf("date" to date.toString())))
                .strings().map(ReservationPeriod::valueOf).toSet()
        }.getOrDefault(emptySet())

    override suspend fun bookedHours(areaId: String, date: LocalDate): Set<Int> =
        runCatching {
            val hours = JSONArray(api.get("areas/$areaId/booked-hours", mapOf("date" to date.toString())))
            List(hours.length()) { hours.getInt(it) }.toSet()
        }.getOrDefault(emptySet())

    // O servidor usa o nome de quem está logado; [residentName] fica para os dados de demonstração.
    override suspend fun reserve(
        areaId: String,
        date: LocalDate,
        slot: ReservationSlot,
        unit: String,
        residentName: String
    ): Result<Reservation> = runCatching {
        val body = JSONObject()
            .put("condoId", condoId())
            .put("areaId", areaId)
            .put("date", date.toString())
            .put("unit", unit)
        when (slot) {
            is ReservationSlot.Period -> body.put("period", slot.period.name)
            is ReservationSlot.Hours -> body.put("startHour", slot.startHour).put("endHour", slot.endHour)
            // O servidor sabe pelo modo do espaço que a reserva é do dia inteiro.
            ReservationSlot.FullDay -> Unit
        }
        api.send("POST", "reservations", body).obj { it.toReservation() }
    }

    override suspend fun cancel(reservationId: String) = api.quietly {
        api.send("POST", "reservations/$reservationId/cancel")
    }

    override suspend fun approve(reservationId: String) = api.quietly {
        api.send("POST", "reservations/$reservationId/approve")
    }

    override suspend fun reject(reservationId: String, reason: String) = api.quietly {
        api.send("POST", "reservations/$reservationId/reject", JSONObject().put("reason", reason.trim()))
    }

    override suspend fun saveArea(area: CommonArea): CommonArea =
        api.send("POST", "areas", area.toJson().put("condoId", area.condoId.ifBlank { condoId() })).obj { it.toArea() }

    override suspend fun deleteArea(areaId: String) = api.quietly {
        api.send("DELETE", "areas/$areaId")
    }

    override suspend fun setAreaClosed(areaId: String, reason: String?) = api.quietly {
        api.send("PUT", "areas/$areaId/closed", JSONObject().put("reason", reason ?: JSONObject.NULL))
    }
}

class ApiServiceBookingRepository(private val api: ApiClient) : ServiceBookingRepository {

    override fun observeBookings(condoId: String, unit: String): Flow<List<ServiceBooking>> =
        api.live { api.get("service-bookings", mapOf("condoId" to condoId, "unit" to unit)).list { it.toBooking() } }

    override fun observeAllBookings(condoId: String): Flow<List<ServiceBooking>> =
        api.live { api.get("service-bookings", mapOf("condoId" to condoId)).list { it.toBooking() } }

    override suspend fun takenMovePeriods(condoId: String, date: LocalDate): Set<ReservationPeriod> =
        runCatching {
            JSONArray(api.get("service-bookings/taken-moves", mapOf("condoId" to condoId, "date" to date.toString())))
                .strings().map(ReservationPeriod::valueOf).toSet()
        }.getOrDefault(emptySet())

    override suspend fun request(booking: ServiceBooking): Result<ServiceBooking> = runCatching {
        api.send(
            "POST", "service-bookings",
            JSONObject()
                .put("condoId", booking.condoId)
                .put("type", booking.type.name)
                .put("date", booking.date.toString())
                .put("period", booking.period.name)
                .put("unit", booking.unit)
                .put("repairKind", booking.repairKind?.name ?: JSONObject.NULL)
                .put("provider", booking.provider.trim())
        ).obj { it.toBooking() }
    }

    override suspend fun cancel(bookingId: String) = api.quietly {
        api.send("POST", "service-bookings/$bookingId/cancel")
    }

    override suspend fun approve(bookingId: String): Result<ServiceBooking> = runCatching {
        api.send("POST", "service-bookings/$bookingId/approve").obj { it.toBooking() }
    }

    override suspend fun reject(bookingId: String, reason: String) = api.quietly {
        api.send("POST", "service-bookings/$bookingId/reject", JSONObject().put("reason", reason.trim()))
    }
}
