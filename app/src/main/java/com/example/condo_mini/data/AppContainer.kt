package com.example.condo_mini.data

import android.content.Context
import com.example.condo_mini.data.pdf.BillPdfGenerator
import com.example.condo_mini.data.pdf.UnitDocumentStore
import com.example.condo_mini.data.fake.FakeBillRepository
import com.example.condo_mini.data.fake.FakeDirectoryRepository
import com.example.condo_mini.data.fake.FakeNoticeRepository
import com.example.condo_mini.data.fake.FakeOccurrenceRepository
import com.example.condo_mini.data.fake.FakeParcelRepository
import com.example.condo_mini.data.fake.ParcelPhotoGenerator
import com.example.condo_mini.data.fake.FakeReservationRepository
import com.example.condo_mini.data.fake.FakeSessionRepository
import com.example.condo_mini.domain.repository.BillRepository
import com.example.condo_mini.domain.repository.DirectoryRepository
import com.example.condo_mini.domain.repository.NoticeRepository
import com.example.condo_mini.domain.repository.OccurrenceRepository
import com.example.condo_mini.domain.repository.ParcelRepository
import com.example.condo_mini.domain.repository.ReservationRepository
import com.example.condo_mini.domain.repository.SessionRepository

/**
 * DI manual. Troque as implementações fake pelas reais (API/Room) quando o
 * backend existir — as telas dependem apenas das interfaces.
 */
object AppContainer {
    private lateinit var appContext: Context

    /** Chamado uma vez em [com.example.condo_mini.CondoApplication.onCreate]. */
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    val sessionRepository: SessionRepository by lazy { FakeSessionRepository(directoryRepository) }
    val noticeRepository: NoticeRepository by lazy { FakeNoticeRepository() }
    val reservationRepository: ReservationRepository by lazy { FakeReservationRepository() }
    val billRepository: BillRepository by lazy {
        FakeBillRepository(BillPdfGenerator(appContext), UnitDocumentStore(appContext))
    }
    val directoryRepository: DirectoryRepository by lazy { FakeDirectoryRepository() }
    val parcelRepository: ParcelRepository by lazy { FakeParcelRepository(ParcelPhotoGenerator(appContext)) }
    val occurrenceRepository: OccurrenceRepository by lazy { FakeOccurrenceRepository() }
}
