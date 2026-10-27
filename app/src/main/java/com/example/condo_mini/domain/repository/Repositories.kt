package com.example.condo_mini.domain.repository

import com.example.condo_mini.domain.model.Bill
import com.example.condo_mini.domain.model.CommonArea
import com.example.condo_mini.domain.model.Notice
import com.example.condo_mini.domain.model.NoticeCategory
import com.example.condo_mini.domain.model.Occurrence
import com.example.condo_mini.domain.model.OccurrenceCategory
import com.example.condo_mini.domain.model.Parcel
import com.example.condo_mini.domain.model.Reservation
import com.example.condo_mini.domain.model.ReservationPeriod
import com.example.condo_mini.domain.model.User
import com.example.condo_mini.domain.model.UserRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.time.LocalDate

interface SessionRepository {
    val currentUser: StateFlow<User?>
    fun login(name: String, unit: String, role: UserRole)
    fun logout()
    fun updateContact(email: String, phone: String)
}

interface NoticeRepository {
    fun observeNotices(): Flow<List<Notice>>
    suspend fun publish(
        title: String,
        body: String,
        category: NoticeCategory,
        pinned: Boolean,
        author: String
    ): Notice

    suspend fun delete(noticeId: String)
}

interface ReservationRepository {
    fun observeAreas(): Flow<List<CommonArea>>
    fun observeReservations(unit: String): Flow<List<Reservation>>

    /** Todas as reservas do condomínio (visão do síndico). */
    fun observeAllReservations(): Flow<List<Reservation>>
    suspend fun bookedPeriods(areaId: String, date: LocalDate): Set<ReservationPeriod>
    suspend fun reserve(
        areaId: String,
        date: LocalDate,
        period: ReservationPeriod,
        unit: String,
        residentName: String
    ): Result<Reservation>

    suspend fun cancel(reservationId: String)
    suspend fun approve(reservationId: String)
    suspend fun reject(reservationId: String, reason: String)
}

interface BillRepository {
    fun observeBills(unit: String): Flow<List<Bill>>
    suspend fun getBill(billId: String, unit: String): Bill?

    /** Arquivo PDF do boleto, pronto para exibir ou compartilhar. */
    suspend fun billPdf(bill: Bill, payer: User): File
}

interface ParcelRepository {
    fun observeParcels(unit: String): Flow<List<Parcel>>
}

interface OccurrenceRepository {
    fun observeOccurrences(unit: String): Flow<List<Occurrence>>

    /** Ocorrências de todas as unidades (visão do síndico). */
    fun observeAllOccurrences(): Flow<List<Occurrence>>
    suspend fun register(
        title: String,
        description: String,
        category: OccurrenceCategory,
        unit: String,
        authorName: String,
        photoUrls: List<String>
    ): Occurrence

    /** Resposta da administração; [resolve] encerra a ocorrência. */
    suspend fun respond(
        occurrenceId: String,
        text: String,
        author: String,
        resolve: Boolean,
        photoUrls: List<String>
    )

    /** Administração pede mais informações ao morador. */
    suspend fun requestDetails(occurrenceId: String, text: String, author: String, photoUrls: List<String>)

    /** Morador complementa a ocorrência após um pedido de detalhes. */
    suspend fun replyAsResident(occurrenceId: String, text: String, author: String, photoUrls: List<String>)
}
