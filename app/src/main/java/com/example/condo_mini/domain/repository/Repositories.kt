package com.example.condo_mini.domain.repository

import com.example.condo_mini.domain.model.Bill
import com.example.condo_mini.domain.model.CommonArea
import com.example.condo_mini.domain.model.CondoUnit
import com.example.condo_mini.domain.model.Condominium
import com.example.condo_mini.domain.model.Notice
import com.example.condo_mini.domain.model.NoticeCategory
import com.example.condo_mini.domain.model.Occurrence
import com.example.condo_mini.domain.model.OccurrenceCategory
import com.example.condo_mini.domain.model.Parcel
import com.example.condo_mini.domain.model.Reservation
import com.example.condo_mini.domain.model.ReservationPeriod
import com.example.condo_mini.domain.model.UnitDocument
import com.example.condo_mini.domain.model.User
import com.example.condo_mini.domain.model.UserRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.time.LocalDate
import java.time.YearMonth

interface SessionRepository {
    val currentUser: StateFlow<User?>
    fun login(name: String, unit: String, role: UserRole)

    /** Troca o apartamento em uso por outro dos [User.units] do morador. */
    fun selectUnit(unit: String)
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
    fun observeAreas(condoId: String): Flow<List<CommonArea>>
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

    /** Cria o espaço quando [CommonArea.id] está vazio; senão atualiza. */
    suspend fun saveArea(area: CommonArea): CommonArea

    /** Remove o espaço e cancela as reservas futuras dele. */
    suspend fun deleteArea(areaId: String)

    /** Interdita com um motivo, ou libera quando [reason] é `null`. */
    suspend fun setAreaClosed(areaId: String, reason: String?)
}

interface BillRepository {
    fun observeBills(unit: String): Flow<List<Bill>>
    suspend fun getBill(billId: String, unit: String): Bill?

    /** Arquivo PDF do boleto, pronto para exibir ou compartilhar. */
    suspend fun billPdf(bill: Bill, payer: User): File

    /** PDFs enviados pela administração, de todas as unidades. */
    fun observeUnitDocuments(): Flow<List<UnitDocument>>

    /** Envia o PDF de [reference] para [unit], substituindo o anterior do mesmo mês. */
    suspend fun uploadUnitDocument(
        condoId: String,
        unit: String,
        reference: YearMonth,
        sourceUri: String
    ): Result<UnitDocument>
    suspend fun deleteUnitDocument(documentId: String)
}

/**
 * Condomínios, unidades e cadastros (visão do administrador). Unidades e pessoas
 * são sempre as do condomínio selecionado em [selectedCondoId].
 */
interface DirectoryRepository {
    fun observeCondos(): Flow<List<Condominium>>
    val selectedCondoId: StateFlow<String>
    fun selectCondo(condoId: String)

    /** Cria o condomínio quando [Condominium.id] está vazio; senão atualiza. */
    suspend fun saveCondo(condo: Condominium): Condominium

    fun observeUnits(): Flow<List<CondoUnit>>
    fun observeUsers(): Flow<List<User>>

    /** Unidades e pessoas de um condomínio específico (portaria), sem depender da seleção do admin. */
    fun observeUnitsOf(condoId: String): Flow<List<CondoUnit>>
    fun observeUsersOf(condoId: String): Flow<List<User>>

    /** Cria o cadastro quando [User.id] está vazio (no condomínio selecionado); senão atualiza. */
    suspend fun saveUser(user: User): User
    suspend fun deleteUser(userId: String)

    /** Liga o usuário a mais um apartamento, mantendo os que ele já tem. */
    suspend fun assignUnit(userId: String, unit: String)

    /** Desliga o usuário de [unit]; os outros apartamentos dele continuam. */
    suspend fun removeUnit(userId: String, unit: String)

    /**
     * Cadastro usado no login, em qualquer condomínio: nome completo, ou só o primeiro
     * nome quando ele não se repete. No backend, isso vira a autenticação.
     */
    fun findUser(name: String, role: UserRole): User?
}

interface ParcelRepository {
    fun observeParcels(condoId: String, unit: String): Flow<List<Parcel>>

    /** Todas as encomendas do condomínio (visão da portaria). */
    fun observeAllParcels(condoId: String): Flow<List<Parcel>>

    /** Registra a chegada; o código de retirada é gerado aqui. */
    suspend fun register(
        condoId: String,
        unit: String,
        description: String,
        carrier: String,
        receivedBy: String,
        photoUrl: String?
    ): Parcel

    /** Entrega ao morador; falha se [code] não for o código da encomenda. */
    suspend fun deliver(parcelId: String, code: String, deliveredBy: String): Result<Parcel>
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
