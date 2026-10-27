package com.example.condo_mini.ui.screens.bills

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.condo_mini.data.AppContainer
import com.example.condo_mini.domain.model.Bill
import com.example.condo_mini.domain.model.BillStatus
import com.example.condo_mini.domain.repository.BillRepository
import com.example.condo_mini.domain.repository.SessionRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class BillsUiState(
    val isLoading: Boolean = true,
    val pending: List<Bill> = emptyList(),
    val paid: List<Bill> = emptyList()
) {
    val totalPending: Double get() = pending.sumOf { it.amount }
    val overdueCount: Int get() = pending.count { it.status == BillStatus.OVERDUE }
    val nextDue: Bill? get() = pending.filter { it.status == BillStatus.OPEN }.minByOrNull { it.dueDate }
}

@OptIn(ExperimentalCoroutinesApi::class)
class BillsViewModel(
    billRepository: BillRepository = AppContainer.billRepository,
    sessionRepository: SessionRepository = AppContainer.sessionRepository
) : ViewModel() {

    val uiState: StateFlow<BillsUiState> = sessionRepository.currentUser
        .flatMapLatest { user -> billRepository.observeBills(user?.unit.orEmpty()) }
        .map { bills ->
            val sorted = bills.sortedByDescending { it.reference }
            BillsUiState(
                isLoading = false,
                pending = sorted.filter { it.status != BillStatus.PAID },
                paid = sorted.filter { it.status == BillStatus.PAID }
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BillsUiState())
}
