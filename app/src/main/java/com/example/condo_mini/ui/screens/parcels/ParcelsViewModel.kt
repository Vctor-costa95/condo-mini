package com.example.condo_mini.ui.screens.parcels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.condo_mini.data.AppContainer
import com.example.condo_mini.domain.model.Parcel
import com.example.condo_mini.domain.model.ParcelStatus
import com.example.condo_mini.domain.repository.ParcelRepository
import com.example.condo_mini.domain.repository.SessionRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class ParcelsUiState(
    val isLoading: Boolean = true,
    val waiting: List<Parcel> = emptyList(),
    val pickedUp: List<Parcel> = emptyList()
)

@OptIn(ExperimentalCoroutinesApi::class)
class ParcelsViewModel(
    parcelRepository: ParcelRepository = AppContainer.parcelRepository,
    sessionRepository: SessionRepository = AppContainer.sessionRepository
) : ViewModel() {

    val uiState: StateFlow<ParcelsUiState> = sessionRepository.currentUser
        .flatMapLatest { user -> parcelRepository.observeParcels(user?.condoId.orEmpty(), user?.unit.orEmpty()) }
        .map { parcels ->
            val sorted = parcels.sortedByDescending { it.arrivedAt }
            ParcelsUiState(
                isLoading = false,
                waiting = sorted.filter { it.status == ParcelStatus.WAITING },
                pickedUp = sorted.filter { it.status == ParcelStatus.PICKED_UP }
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ParcelsUiState())
}
