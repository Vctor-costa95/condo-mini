package com.example.condo_mini.ui.screens.admin.condos

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.condo_mini.data.AppContainer
import com.example.condo_mini.domain.model.Condominium
import com.example.condo_mini.domain.repository.DirectoryRepository
import com.example.condo_mini.ui.components.Eyebrow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class CondoPickerUiState(
    val condos: List<Condominium> = emptyList(),
    val selectedId: String = ""
)

class CondoPickerViewModel(
    private val directoryRepository: DirectoryRepository = AppContainer.directoryRepository
) : ViewModel() {

    val uiState: StateFlow<CondoPickerUiState> = combine(
        directoryRepository.observeCondos(),
        directoryRepository.selectedCondoId,
        ::CondoPickerUiState
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CondoPickerUiState())

    fun onSelect(condo: Condominium) = directoryRepository.selectCondo(condo.id)
}

/** Troca o condomínio em gestão; todas as abas do administrador passam a mostrar o escolhido. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CondoPickerSheet(
    onEdit: (Condominium) -> Unit,
    onCreate: () -> Unit,
    onDismiss: () -> Unit,
    viewModel: CondoPickerViewModel = viewModel()
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
            Eyebrow("Carteira")
            Spacer(modifier = Modifier.height(6.dp))
            Text("Condomínios", style = MaterialTheme.typography.headlineLarge)
            Spacer(modifier = Modifier.height(16.dp))
            uiState.condos.forEach { condo ->
                CondoRow(
                    condo = condo,
                    selected = condo.id == uiState.selectedId,
                    onClick = {
                        viewModel.onSelect(condo)
                        onDismiss()
                    },
                    onEdit = { onEdit(condo) }
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedButton(
                onClick = onCreate,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Novo condomínio", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun CondoRow(condo: Condominium, selected: Boolean, onClick: () -> Unit, onEdit: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = scheme.surfaceContainerLow,
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) scheme.tertiary else scheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(start = 18.dp, end = 6.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        condo.name,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (selected) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Selecionado",
                            tint = scheme.tertiary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Text(
                    condo.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "${condo.towers.size} ${if (condo.towers.size == 1) "bloco" else "blocos"} · ${condo.units.size} apartamentos",
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.outline
                )
            }
            TextButton(onClick = onEdit) {
                Text("Editar", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
