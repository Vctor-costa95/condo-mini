package com.example.condo_mini.ui.screens.admin.condos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.condo_mini.data.AppContainer
import com.example.condo_mini.domain.model.Condominium
import com.example.condo_mini.domain.model.GROUND_FLOOR
import com.example.condo_mini.domain.model.Tower
import com.example.condo_mini.domain.repository.DirectoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

const val MAX_FLOORS = 40
const val MAX_UNITS_PER_FLOOR = 12

data class CondoEditorUiState(
    val isLoading: Boolean = true,
    val isNew: Boolean = true,
    val name: String = "",
    val address: String = "",
    val towers: List<Tower> = emptyList(),
    val isSaving: Boolean = false,
    val error: String? = null,
    /** Vira `true` depois de salvar; a tela então fecha. */
    val saved: Boolean = false
) {
    val totalUnits: Int get() = towers.sumOf { it.totalUnits }
}

/** [condoId] nulo cria um condomínio novo. */
class CondoEditorViewModel(
    private val condoId: String?,
    private val directoryRepository: DirectoryRepository = AppContainer.directoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CondoEditorUiState())
    val uiState: StateFlow<CondoEditorUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val existing = condoId?.let { id -> directoryRepository.observeCondos().first().firstOrNull { it.id == id } }
            _uiState.value = if (existing != null) {
                CondoEditorUiState(isLoading = false, isNew = false, name = existing.name, address = existing.address, towers = existing.towers)
            } else {
                // Ponto de partida comum: um bloco de 10 andares com 4 aptos.
                CondoEditorUiState(isLoading = false, towers = listOf(newTower(index = 0)))
            }
        }
    }

    fun onNameChanged(value: String) = _uiState.update { it.copy(name = value.take(48), error = null) }

    fun onAddressChanged(value: String) = _uiState.update { it.copy(address = value.take(90), error = null) }

    fun onAddTower() = _uiState.update { state ->
        // Próxima letra livre: depois de remover o "Bloco B", o novo volta a ser B.
        val used = state.towers.map { it.name }.toSet()
        val index = (0 until 26).firstOrNull { "Bloco ${'A' + it}" !in used } ?: state.towers.size
        state.copy(towers = state.towers + newTower(index), error = null)
    }

    fun onRemoveTower(towerId: String) = _uiState.update { state ->
        state.copy(towers = state.towers.filterNot { it.id == towerId })
    }

    fun onTowerNameChanged(towerId: String, name: String) = updateTower(towerId) { it.copy(name = name.take(24)) }

    fun onFloorsChanged(towerId: String, floors: Int) = updateTower(towerId) { tower ->
        val value = floors.coerceIn(1, MAX_FLOORS)
        // Exceções acima do novo último andar deixam de existir.
        tower.copy(floors = value, floorOverrides = tower.floorOverrides.filterKeys { it <= value })
    }

    fun onUnitsPerFloorChanged(towerId: String, units: Int) = updateTower(towerId) { tower ->
        val value = units.coerceIn(1, MAX_UNITS_PER_FLOOR)
        // Andares que ficaram iguais ao novo padrão perdem a exceção; o térreo tem padrão próprio (0).
        val overrides = tower.floorOverrides.filter { (floor, count) -> floor == GROUND_FLOOR || count != value }
        tower.copy(unitsPerFloor = value, floorOverrides = overrides)
    }

    /** 0 deixa o andar sem apartamentos; o valor padrão do andar remove a exceção. */
    fun onFloorUnitsChanged(towerId: String, floor: Int, units: Int) = updateTower(towerId) { tower ->
        val value = units.coerceIn(0, MAX_UNITS_PER_FLOOR)
        val overrides = if (value == tower.defaultUnitsOn(floor)) {
            tower.floorOverrides - floor
        } else {
            tower.floorOverrides + (floor to value)
        }
        tower.copy(floorOverrides = overrides)
    }

    fun onSave() {
        val state = _uiState.value
        val names = state.towers.map { it.name.trim().lowercase() }
        val error = when {
            state.name.isBlank() -> "Dê um nome ao condomínio."
            state.towers.isEmpty() -> "Adicione pelo menos um bloco."
            names.any { it.isBlank() } -> "Todos os blocos precisam de nome."
            names.size != names.toSet().size -> "Dois blocos estão com o mesmo nome."
            state.towers.any { it.totalUnits == 0 } ->
                "${state.towers.first { it.totalUnits == 0 }.name.trim()} ficou sem nenhum apartamento."
            else -> null
        }
        if (error != null) {
            _uiState.update { it.copy(error = error) }
            return
        }
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val saved = directoryRepository.saveCondo(
                Condominium(
                    id = condoId.orEmpty(),
                    name = state.name.trim(),
                    address = state.address.trim(),
                    towers = state.towers.map { it.copy(name = it.name.trim()) }
                )
            )
            directoryRepository.selectCondo(saved.id)
            _uiState.update { it.copy(isSaving = false, saved = true) }
        }
    }

    private fun updateTower(towerId: String, transform: (Tower) -> Tower) = _uiState.update { state ->
        state.copy(towers = state.towers.map { if (it.id == towerId) transform(it) else it }, error = null)
    }

    private fun newTower(index: Int): Tower = Tower(
        id = UUID.randomUUID().toString(),
        name = "Bloco ${'A' + index}",
        floors = 10,
        unitsPerFloor = 4
    )
}
