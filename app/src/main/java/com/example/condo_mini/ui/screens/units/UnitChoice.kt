package com.example.condo_mini.ui.screens.units

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.condo_mini.data.AppContainer
import com.example.condo_mini.domain.model.OccurrenceStatus
import com.example.condo_mini.domain.model.ParcelStatus
import com.example.condo_mini.domain.repository.OccurrenceRepository
import com.example.condo_mini.domain.repository.ParcelRepository
import com.example.condo_mini.domain.repository.SessionRepository
import com.example.condo_mini.ui.components.Eyebrow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/** Um apartamento do morador, com o que está esperando por ele lá. */
data class UnitOption(
    val label: String,
    val waitingParcels: Int,
    val pendingOccurrences: Int
) {
    /** "Apto 1402" */
    val title: String get() = label.substringBefore(" - ")

    /** "Bloco A" */
    val tower: String get() = label.substringAfter(" - ", "")
}

data class UnitChoiceUiState(
    val firstName: String = "",
    val current: String = "",
    val options: List<UnitOption> = emptyList()
)

@OptIn(ExperimentalCoroutinesApi::class)
class UnitChoiceViewModel(
    private val sessionRepository: SessionRepository = AppContainer.sessionRepository,
    private val parcelRepository: ParcelRepository = AppContainer.parcelRepository,
    private val occurrenceRepository: OccurrenceRepository = AppContainer.occurrenceRepository
) : ViewModel() {

    val uiState: StateFlow<UnitChoiceUiState> = sessionRepository.currentUser
        .flatMapLatest { user ->
            val units = user?.units.orEmpty()
            if (user == null || units.isEmpty()) return@flatMapLatest flowOf(UnitChoiceUiState())
            combine(units.map { unit -> optionFor(unit) }) { options ->
                UnitChoiceUiState(
                    firstName = user.name.substringBefore(" "),
                    current = user.unit,
                    options = options.toList()
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UnitChoiceUiState())

    fun onSelect(unit: String) = sessionRepository.selectUnit(unit)

    private fun optionFor(unit: String) = combine(
        parcelRepository.observeParcels(unit),
        occurrenceRepository.observeOccurrences(unit)
    ) { parcels, occurrences ->
        UnitOption(
            label = unit,
            waitingParcels = parcels.count { it.status == ParcelStatus.WAITING },
            pendingOccurrences = occurrences.count { it.status == OccurrenceStatus.NEEDS_INFO }
        )
    }
}

/** Tela logo após o login, para quem tem mais de um apartamento. */
@Composable
fun UnitChoiceScreen(
    onChosen: () -> Unit,
    viewModel: UnitChoiceViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp)
    ) {
        Eyebrow(if (uiState.firstName.isBlank()) "Bem-vindo" else "Olá, ${uiState.firstName}")
        Spacer(modifier = Modifier.height(8.dp))
        Text("Qual apartamento você quer ver?", style = MaterialTheme.typography.displaySmall)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "Você tem ${uiState.options.size} apartamentos neste condomínio. Dá para trocar depois tocando no nome do apartamento, no topo do app.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(28.dp))
        UnitOptionList(
            options = uiState.options,
            current = null,
            onSelect = {
                viewModel.onSelect(it)
                onChosen()
            }
        )
    }
}

/** A mesma escolha, em folha, para trocar de apartamento a qualquer momento. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnitSwitcherSheet(
    onDismiss: () -> Unit,
    viewModel: UnitChoiceViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            Eyebrow("Seus apartamentos")
            Spacer(modifier = Modifier.height(6.dp))
            Text("Trocar de apartamento", style = MaterialTheme.typography.headlineLarge)
            Spacer(modifier = Modifier.height(20.dp))
            UnitOptionList(
                options = uiState.options,
                current = uiState.current,
                onSelect = {
                    viewModel.onSelect(it)
                    onDismiss()
                }
            )
        }
    }
}

@Composable
private fun UnitOptionList(options: List<UnitOption>, current: String?, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        options.forEach { option ->
            UnitOptionCard(option, selected = option.label == current, onClick = { onSelect(option.label) })
        }
    }
}

@Composable
private fun UnitOptionCard(option: UnitOption, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraLarge,
        color = scheme.surfaceContainerLow,
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) scheme.tertiary else scheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Eyebrow(option.tower)
                Spacer(modifier = Modifier.height(4.dp))
                Text(option.title, style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    summary(option),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            if (selected) {
                Icon(Icons.Default.CheckCircle, contentDescription = "Em uso", tint = scheme.tertiary)
            } else {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

private fun summary(option: UnitOption): String {
    val parts = buildList {
        when (option.waitingParcels) {
            0 -> Unit
            1 -> add("1 encomenda aguardando")
            else -> add("${option.waitingParcels} encomendas aguardando")
        }
        when (option.pendingOccurrences) {
            0 -> Unit
            1 -> add("1 ocorrência pede detalhes")
            else -> add("${option.pendingOccurrences} ocorrências pedem detalhes")
        }
    }
    return parts.joinToString(" · ").ifBlank { "Tudo em dia" }
}
