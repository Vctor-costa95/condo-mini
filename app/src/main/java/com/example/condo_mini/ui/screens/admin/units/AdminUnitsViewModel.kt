package com.example.condo_mini.ui.screens.admin.units

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.condo_mini.data.AppContainer
import com.example.condo_mini.domain.model.CondoUnit
import com.example.condo_mini.domain.model.UnitDocument
import com.example.condo_mini.domain.model.User
import com.example.condo_mini.domain.model.UserRole
import com.example.condo_mini.domain.repository.BillRepository
import com.example.condo_mini.domain.repository.DirectoryRepository
import com.example.condo_mini.ui.util.formatMonth
import com.example.condo_mini.ui.util.monthName
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.YearMonth

data class UnitRow(
    val unit: CondoUnit,
    val residents: List<User>,
    /** PDF do mês selecionado, se já enviado. */
    val document: UnitDocument?
)

data class UnitDetail(
    val unit: CondoUnit,
    val residents: List<User>,
    val documents: List<UnitDocument>,
    /** Moradores cadastrados ainda sem apartamento. */
    val unassigned: List<User>
)

data class AdminUnitsUiState(
    val isLoading: Boolean = true,
    val blocks: List<String> = emptyList(),
    val block: String = "",
    val months: List<YearMonth> = emptyList(),
    val month: YearMonth = YearMonth.now(),
    val rows: List<UnitRow> = emptyList(),
    val sentInMonth: Int = 0,
    val totalUnits: Int = 0,
    val detail: UnitDetail? = null,
    /** Unidades com envio de PDF em andamento. */
    val uploading: Set<String> = emptySet(),
    val userMessage: String? = null
)

private data class Filters(val block: String?, val month: YearMonth, val openUnitId: String?)

class AdminUnitsViewModel(
    private val directoryRepository: DirectoryRepository = AppContainer.directoryRepository,
    private val billRepository: BillRepository = AppContainer.billRepository
) : ViewModel() {

    // Próximo mês primeiro: é o boleto que a administração costuma estar enviando.
    private val months = (-1..1).map { YearMonth.now().minusMonths(it.toLong()) }
    private val filters = MutableStateFlow(Filters(block = null, month = months.first(), openUnitId = null))
    private val uploading = MutableStateFlow<Set<String>>(emptySet())
    private val userMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<AdminUnitsUiState> = combine(
        directoryRepository.observeUnits(),
        directoryRepository.observeUsers(),
        billRepository.observeUnitDocuments(),
        filters,
        combine(uploading, userMessage, ::Pair)
    ) { units, users, documents, filters, (uploading, message) ->
        val blocks = units.map { it.block }.distinct()
        val block = filters.block ?: blocks.firstOrNull().orEmpty()
        val residentsByUnit = users.filter { it.role == UserRole.RESIDENT }.groupBy { it.unit }
        val monthDocs = documents.filter { it.reference == filters.month }.associateBy { it.unit }
        AdminUnitsUiState(
            isLoading = false,
            blocks = blocks,
            block = block,
            months = months,
            month = filters.month,
            rows = units.filter { it.block == block }.map { unit ->
                UnitRow(unit, residentsByUnit[unit.label].orEmpty(), monthDocs[unit.label])
            },
            sentInMonth = units.count { it.label in monthDocs },
            totalUnits = units.size,
            detail = units.firstOrNull { it.id == filters.openUnitId }?.let { unit ->
                UnitDetail(
                    unit = unit,
                    residents = residentsByUnit[unit.label].orEmpty(),
                    documents = documents.filter { it.unit == unit.label }.sortedByDescending { it.reference },
                    unassigned = users.filter { it.role == UserRole.RESIDENT && it.unit.isBlank() }
                )
            },
            uploading = uploading,
            userMessage = message
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AdminUnitsUiState())

    fun onBlockSelected(block: String) = filters.update { it.copy(block = block) }

    fun onMonthSelected(month: YearMonth) = filters.update { it.copy(month = month) }

    fun onOpenUnit(unit: CondoUnit) = filters.update { it.copy(openUnitId = unit.id) }

    fun onCloseUnit() = filters.update { it.copy(openUnitId = null) }

    /** [uri] vem do seletor de documentos do sistema. */
    fun onPdfPicked(unit: CondoUnit, uri: String) {
        val month = filters.value.month
        uploading.update { it + unit.id }
        viewModelScope.launch {
            billRepository.uploadUnitDocument(unit.label, month, uri)
                .onSuccess { userMessage.value = "Boleto de ${month.monthName()} enviado para o ${unit.number} ${unit.block}." }
                .onFailure { userMessage.value = it.message ?: "Não foi possível enviar o PDF." }
            uploading.update { it - unit.id }
        }
    }

    fun onDeleteDocument(document: UnitDocument) {
        viewModelScope.launch {
            billRepository.deleteUnitDocument(document.id)
            userMessage.value = "PDF de ${document.reference.formatMonth()} removido."
        }
    }

    fun onAssign(user: User, unit: CondoUnit) {
        viewModelScope.launch {
            directoryRepository.assignUnit(user.id, unit.label)
            userMessage.value = "${user.name} agora mora no ${unit.number} ${unit.block}."
        }
    }

    fun onUnassign(user: User) {
        viewModelScope.launch {
            directoryRepository.assignUnit(user.id, "")
            userMessage.value = "${user.name} ficou sem unidade."
        }
    }

    fun onMessageShown() {
        userMessage.value = null
    }
}
