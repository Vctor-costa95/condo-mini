package com.example.condo_mini.data

import android.content.Context
import com.example.condo_mini.BuildConfig
import com.example.condo_mini.data.fake.FakeBillRepository
import com.example.condo_mini.data.fake.FakeDirectoryRepository
import com.example.condo_mini.data.fake.FakeDocumentRepository
import com.example.condo_mini.data.fake.FakeNoticeRepository
import com.example.condo_mini.data.fake.FakeOccurrenceRepository
import com.example.condo_mini.data.fake.FakeParcelRepository
import com.example.condo_mini.data.fake.FakeReservationRepository
import com.example.condo_mini.data.fake.FakeServiceBookingRepository
import com.example.condo_mini.data.fake.FakeSessionRepository
import com.example.condo_mini.data.fake.ParcelPhotoGenerator
import com.example.condo_mini.data.pdf.BillPdfGenerator
import com.example.condo_mini.data.pdf.CondoDocumentStore
import com.example.condo_mini.data.pdf.DocumentPdfGenerator
import com.example.condo_mini.data.pdf.UnitDocumentStore
import com.example.condo_mini.data.remote.ApiBillRepository
import com.example.condo_mini.data.remote.ApiClient
import com.example.condo_mini.data.remote.ApiDirectoryRepository
import com.example.condo_mini.data.remote.ApiDocumentRepository
import com.example.condo_mini.data.remote.ApiNoticeRepository
import com.example.condo_mini.data.remote.ApiOccurrenceRepository
import com.example.condo_mini.data.remote.ApiParcelRepository
import com.example.condo_mini.data.remote.ApiReservationRepository
import com.example.condo_mini.data.remote.ApiServiceBookingRepository
import com.example.condo_mini.data.remote.ApiSessionRepository
import com.example.condo_mini.data.remote.SessionStore
import com.example.condo_mini.push.PushNotifications
import com.example.condo_mini.domain.model.UserRole
import com.example.condo_mini.domain.repository.BillRepository
import com.example.condo_mini.domain.repository.DirectoryRepository
import com.example.condo_mini.domain.repository.DocumentRepository
import com.example.condo_mini.domain.repository.NoticeRepository
import com.example.condo_mini.domain.repository.OccurrenceRepository
import com.example.condo_mini.domain.repository.ParcelRepository
import com.example.condo_mini.domain.repository.ReservationRepository
import com.example.condo_mini.domain.repository.ServiceBookingRepository
import com.example.condo_mini.domain.repository.SessionRepository

/**
 * DI manual. Com `BuildConfig.API_BASE_URL` preenchido, os dados vêm da API;
 * vazio, dos repositórios Fake em memória. As telas dependem só das interfaces.
 */
object AppContainer {
    private lateinit var appContext: Context

    /** Chamado uma vez em [com.example.condo_mini.CondoApplication.onCreate]. */
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /** null quando o app roda só com os dados de demonstração. */
    val apiClient: ApiClient? by lazy {
        BuildConfig.API_BASE_URL.takeIf { it.isNotBlank() }?.let { ApiClient(it, appContext) }
    }

    /** O condomínio do cadastro; para o administrador, o escolhido no seletor. */
    private val currentCondoId: () -> String = {
        val user = sessionRepository.currentUser.value
        if (user == null || user.role == UserRole.ADMIN) directoryRepository.selectedCondoId.value else user.condoId
    }

    val sessionRepository: SessionRepository by lazy {
        apiClient?.let { api -> ApiSessionRepository(api, SessionStore(appContext)) { PushNotifications.registerDevice(api) } }
            ?: FakeSessionRepository(directoryRepository as FakeDirectoryRepository)
    }
    val noticeRepository: NoticeRepository by lazy {
        apiClient?.let { ApiNoticeRepository(it, currentCondoId) } ?: FakeNoticeRepository()
    }
    val reservationRepository: ReservationRepository by lazy {
        apiClient?.let { ApiReservationRepository(it, currentCondoId) } ?: FakeReservationRepository()
    }
    val serviceBookingRepository: ServiceBookingRepository by lazy {
        apiClient?.let { ApiServiceBookingRepository(it) } ?: FakeServiceBookingRepository()
    }
    val billRepository: BillRepository by lazy {
        apiClient?.let { ApiBillRepository(it, appContext, BillPdfGenerator(appContext), currentCondoId) }
            ?: FakeBillRepository(BillPdfGenerator(appContext), UnitDocumentStore(appContext))
    }
    val directoryRepository: DirectoryRepository by lazy {
        apiClient?.let { ApiDirectoryRepository(it) } ?: FakeDirectoryRepository()
    }
    val parcelRepository: ParcelRepository by lazy {
        apiClient?.let { ApiParcelRepository(it) } ?: FakeParcelRepository(ParcelPhotoGenerator(appContext))
    }
    val occurrenceRepository: OccurrenceRepository by lazy {
        apiClient?.let { ApiOccurrenceRepository(it, currentCondoId) } ?: FakeOccurrenceRepository()
    }
    val documentRepository: DocumentRepository by lazy {
        apiClient?.let { ApiDocumentRepository(it, appContext, DocumentPdfGenerator(appContext)) }
            ?: FakeDocumentRepository(CondoDocumentStore(appContext), DocumentPdfGenerator(appContext))
    }
}
