package com.example.condo_mini.ui.screens.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.condo_mini.data.AppContainer
import com.example.condo_mini.domain.model.OccurrenceStatus
import com.example.condo_mini.domain.model.ParcelStatus
import com.example.condo_mini.domain.model.ReservationStatus
import com.example.condo_mini.domain.model.User
import com.example.condo_mini.domain.model.UserRole
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
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(
    sessionRepository: SessionRepository = AppContainer.sessionRepository,
    private val parcelRepository: ParcelRepository = AppContainer.parcelRepository,
    private val occurrenceRepository: OccurrenceRepository = AppContainer.occurrenceRepository,
    private val reservationRepository: ReservationRepository = AppContainer.reservationRepository
) : ViewModel() {

    val user: StateFlow<User?> = sessionRepository.currentUser

    /** Itens que pedem ação do usuário, exibidos como contador na aba. */
    val badges: StateFlow<Map<AppTab, Int>> = user
        .flatMapLatest { user ->
            when (user?.role) {
                UserRole.MANAGER -> managerBadges()
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
