package com.example.condo_mini.ui.screens.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.condo_mini.data.AppContainer
import com.example.condo_mini.domain.model.Condominium
import com.example.condo_mini.domain.model.OccurrenceStatus
import com.example.condo_mini.domain.model.ParcelStatus
import com.example.condo_mini.domain.model.ReservationStatus
import com.example.condo_mini.domain.model.User
import com.example.condo_mini.domain.model.UserRole
import com.example.condo_mini.domain.repository.DirectoryRepository
import com.example.condo_mini.domain.repository.OccurrenceRepository
import com.example.condo_mini.domain.repository.ParcelRepository
import com.example.condo_mini.domain.repository.ReservationRepository
import com.example.condo_mini.domain.repository.SessionRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(
    sessionRepository: SessionRepository = AppContainer.sessionRepository,
    private val parcelRepository: ParcelRepository = AppContainer.parcelRepository,
    private val occurrenceRepository: OccurrenceRepository = AppContainer.occurrenceRepository,
    private val reservationRepository: ReservationRepository = AppContainer.reservationRepository,
    private val directoryRepository: DirectoryRepository = AppContainer.directoryRepository
) : ViewModel() {

    val user: StateFlow<User?> = sessionRepository.currentUser

    /** Condomínio em gestão pelo administrador, exibido no cabeçalho. */
    val adminCondo: StateFlow<Condominium?> = combine(
        directoryRepository.observeCondos(),
        directoryRepository.selectedCondoId
    ) { condos, id -> condos.firstOrNull { it.id == id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Itens que pedem ação do usuário, exibidos como contador na aba. */
    val badges: StateFlow<Map<AppTab, Int>> = user
        .flatMapLatest { user ->
            when (user?.role) {
                UserRole.MANAGER -> managerBadges()
                UserRole.ADMIN -> adminBadges()
                null -> flowOf(emptyMap())
                else -> residentBadges(user.unit)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private fun residentBadges(unit: String): Flow<Map<AppTab, Int>> = combine(
        parcelRepository.observeParcels(unit),
        occurrenceRepository.observeOccurrences(unit)
    ) { parcels, occurrences ->
        mapOf(
            AppTab.PARCELS to parcels.count { it.status == ParcelStatus.WAITING },
            AppTab.OCCURRENCES to occurrences.count { it.status == OccurrenceStatus.NEEDS_INFO }
        )
    }

    // Moradores cadastrados que ainda não foram alocados em um apartamento.
    private fun adminBadges(): Flow<Map<AppTab, Int>> = directoryRepository.observeUsers().map { users ->
        mapOf(AppTab.ADMIN_USERS to users.count { it.role == UserRole.RESIDENT && it.unit.isBlank() })
    }

    private fun managerBadges(): Flow<Map<AppTab, Int>> = combine(
        reservationRepository.observeAllReservations(),
        occurrenceRepository.observeAllOccurrences()
    ) { reservations, occurrences ->
        mapOf(
            AppTab.MANAGER_RESERVATIONS to reservations.count { it.status == ReservationStatus.PENDING },
            AppTab.MANAGER_OCCURRENCES to occurrences.count {
                it.status == OccurrenceStatus.OPEN || it.status == OccurrenceStatus.IN_REVIEW
            }
        )
    }
}
