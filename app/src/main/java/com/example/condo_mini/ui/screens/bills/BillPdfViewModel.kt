package com.example.condo_mini.ui.screens.bills

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.condo_mini.data.AppContainer
import com.example.condo_mini.domain.model.Bill
import com.example.condo_mini.domain.repository.BillRepository
import com.example.condo_mini.domain.repository.SessionRepository
import com.example.condo_mini.ui.util.renderPdfPages
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

data class BillPdfUiState(
    val isLoading: Boolean = true,
    val bill: Bill? = null,
    val file: File? = null,
    val pages: List<ImageBitmap> = emptyList(),
    val error: String? = null
)

class BillPdfViewModel(
    private val billId: String,
    private val billRepository: BillRepository = AppContainer.billRepository,
    private val sessionRepository: SessionRepository = AppContainer.sessionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BillPdfUiState())
    val uiState: StateFlow<BillPdfUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val user = sessionRepository.currentUser.value
            val bill = user?.let { billRepository.getBill(billId, it.unit) }
            if (user == null || bill == null) {
                _uiState.value = BillPdfUiState(isLoading = false, error = "Boleto não encontrado.")
                return@launch
            }
            runCatching {
                val file = billRepository.billPdf(bill, user)
                file to renderPdfPages(file)
            }.onSuccess { (file, pages) ->
                _uiState.value = BillPdfUiState(isLoading = false, bill = bill, file = file, pages = pages)
            }.onFailure {
                _uiState.value = BillPdfUiState(isLoading = false, bill = bill, error = "Não foi possível abrir o PDF.")
            }
        }
    }
}
