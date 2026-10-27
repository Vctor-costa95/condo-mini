package com.example.condo_mini.ui.screens.bills

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.condo_mini.data.AppContainer
import com.example.condo_mini.domain.model.Bill
import com.example.condo_mini.domain.repository.BillRepository
import com.example.condo_mini.domain.repository.SessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
                file to renderPages(file)
            }.onSuccess { (file, pages) ->
                _uiState.value = BillPdfUiState(isLoading = false, bill = bill, file = file, pages = pages)
            }.onFailure {
                _uiState.value = BillPdfUiState(isLoading = false, bill = bill, error = "Não foi possível abrir o PDF.")
            }
        }
    }

    private suspend fun renderPages(file: File): List<ImageBitmap> = withContext(Dispatchers.IO) {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                (0 until renderer.pageCount).map { index ->
                    renderer.openPage(index).use { page ->
                        val width = RENDER_WIDTH_PX
                        val height = width * page.height / page.width
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmap.asImageBitmap()
                    }
                }
            }
        }
    }

    private companion object {
        // ~150 dpi em A4: nítido no zoom sem pesar na memória.
        const val RENDER_WIDTH_PX = 1240
    }
}
