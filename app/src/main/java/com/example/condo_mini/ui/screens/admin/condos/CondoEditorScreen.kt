package com.example.condo_mini.ui.screens.admin.condos

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.condo_mini.domain.model.Tower
import com.example.condo_mini.ui.components.CondoCard
import com.example.condo_mini.ui.components.Eyebrow
import com.example.condo_mini.ui.components.LoadingState
import com.example.condo_mini.ui.components.PrimaryButton
import com.example.condo_mini.ui.components.SectionHeader
import com.example.condo_mini.ui.components.condoTextFieldColors
import com.example.condo_mini.ui.theme.ChampagneLight
import com.example.condo_mini.ui.theme.CondoTheme

@Composable
fun CondoEditorRoute(
    condoId: String?,
    onBackClick: () -> Unit,
    viewModel: CondoEditorViewModel = viewModel(key = "condo-editor-${condoId ?: "new"}") { CondoEditorViewModel(condoId) }
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(uiState.saved) {
        if (uiState.saved) onBackClick()
    }
    CondoEditorScreen(
        uiState = uiState,
        onBackClick = onBackClick,
        onNameChanged = viewModel::onNameChanged,
        onAddressChanged = viewModel::onAddressChanged,
        onAddTower = viewModel::onAddTower,
        onRemoveTower = viewModel::onRemoveTower,
        onTowerNameChanged = viewModel::onTowerNameChanged,
        onFloorsChanged = viewModel::onFloorsChanged,
        onUnitsPerFloorChanged = viewModel::onUnitsPerFloorChanged,
        onFloorUnitsChanged = viewModel::onFloorUnitsChanged,
        onSave = viewModel::onSave
    )
}

@Composable
fun CondoEditorScreen(
    uiState: CondoEditorUiState,
    onBackClick: () -> Unit,
    onNameChanged: (String) -> Unit,
    onAddressChanged: (String) -> Unit,
    onAddTower: () -> Unit,
    onRemoveTower: (String) -> Unit,
    onTowerNameChanged: (String, String) -> Unit,
    onFloorsChanged: (String, Int) -> Unit,
    onUnitsPerFloorChanged: (String, Int) -> Unit,
    onFloorUnitsChanged: (String, Int, Int) -> Unit,
    onSave: () -> Unit
) {
    // Andar sendo ajustado: torre + número do andar.
    var editingFloor by remember { mutableStateOf<Pair<Tower, Int>?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 8.dp, end = 20.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
            }
            Spacer(modifier = Modifier.width(4.dp))
            Column {
                Eyebrow(if (uiState.isNew) "Novo condomínio" else "Editar condomínio")
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    uiState.name.ifBlank { "Sem nome" },
                    style = MaterialTheme.typography.headlineLarge,
                    maxLines = 1
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        if (uiState.isLoading) {
            LoadingState()
            return@Column
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            OutlinedTextField(
                value = uiState.name,
                onValueChange = onNameChanged,
                label = { Text("Nome do condomínio") },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                colors = condoTextFieldColors(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = uiState.address,
                onValueChange = onAddressChanged,
                label = { Text("Endereço") },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                colors = condoTextFieldColors(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))
            SectionHeader(
                "Blocos e torres",
                trailing = "${uiState.towers.size} · ${uiState.totalUnits} aptos"
            )
            uiState.towers.forEach { tower ->
                Spacer(modifier = Modifier.height(10.dp))
                TowerCard(
                    tower = tower,
                    canRemove = uiState.towers.size > 1,
                    onRemove = { onRemoveTower(tower.id) },
                    onNameChanged = { onTowerNameChanged(tower.id, it) },
                    onFloorsChanged = { onFloorsChanged(tower.id, it) },
                    onUnitsPerFloorChanged = { onUnitsPerFloorChanged(tower.id, it) },
                    onFloorClick = { floor -> editingFloor = tower to floor }
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = onAddTower,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Adicionar bloco", style = MaterialTheme.typography.labelLarge)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            uiState.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(8.dp))
            }
            PrimaryButton(
                text = if (uiState.isNew) "Criar condomínio" else "Salvar alterações",
                onClick = onSave,
                loading = uiState.isSaving
            )
        }
    }

    editingFloor?.let { (tower, floor) ->
        // Lê a torre atual: os passos do diálogo atualizam o estado enquanto ele está aberto.
        val current = uiState.towers.firstOrNull { it.id == tower.id } ?: tower
        FloorDialog(
            tower = current,
            floor = floor,
            onApply = { units ->
                onFloorUnitsChanged(current.id, floor, units)
                editingFloor = null
            },
            onDismiss = { editingFloor = null }
        )
    }
}

@Composable
private fun TowerCard(
    tower: Tower,
    canRemove: Boolean,
    onRemove: () -> Unit,
    onNameChanged: (String) -> Unit,
    onFloorsChanged: (Int) -> Unit,
    onUnitsPerFloorChanged: (Int) -> Unit,
    onFloorClick: (Int) -> Unit
) {
    CondoCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = tower.name,
                onValueChange = onNameChanged,
                label = { Text("Nome do bloco") },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                colors = condoTextFieldColors(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.weight(1f)
            )
            if (canRemove) {
                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = "Remover ${tower.name}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Stepper("Andares", tower.floors, 1, MAX_FLOORS, onFloorsChanged, Modifier.weight(1f))
            Stepper("Aptos por andar", tower.unitsPerFloor, 1, MAX_UNITS_PER_FLOOR, onUnitsPerFloorChanged, Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(16.dp))
        TowerElevation(tower = tower, onFloorClick = onFloorClick)
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            buildString {
                append("${tower.totalUnits} apartamentos. Toque em um andar para mudar a quantidade dele")
                if (tower.floorOverrides.isEmpty()) {
                    append(", como uma cobertura.")
                } else {
                    append(". Diferentes: ")
                    append(
                        tower.floorOverrides.toSortedMap().entries.joinToString { (floor, units) ->
                            "${floor}º com $units"
                        }
                    )
                    append(".")
                }
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun Stepper(
    label: String,
    value: Int,
    min: Int,
    max: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onValueChange(value - 1) }, enabled = value > min) {
                Icon(Icons.Default.Remove, contentDescription = "Diminuir $label")
            }
            Text(
                "$value",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(40.dp)
            )
            IconButton(onClick = { onValueChange(value + 1) }, enabled = value < max) {
                Icon(Icons.Default.Add, contentDescription = "Aumentar $label")
            }
        }
    }
}

/**
 * Fachada do bloco à noite: um andar por linha, uma janela acesa por apartamento.
 * Andares fora do padrão acendem em champanhe.
 */
@Composable
private fun TowerElevation(tower: Tower, onFloorClick: (Int) -> Unit) {
    val condo = CondoTheme.colors
    val rowHeight: Dp = when {
        tower.floors <= 16 -> 20.dp
        tower.floors <= 28 -> 15.dp
        else -> 12.dp
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(condo.heroEnd)
            .padding(start = 8.dp, end = 14.dp, top = 14.dp, bottom = 8.dp)
    ) {
        (tower.floors downTo 1).forEach { floor ->
            val units = tower.unitsOn(floor)
            val different = floor in tower.floorOverrides
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(rowHeight)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .clickable(onClickLabel = "Ajustar ${floor}º andar") { onFloorClick(floor) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "$floor",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (different) condo.accent else condo.onHeroMuted,
                    textAlign = TextAlign.End,
                    modifier = Modifier
                        .width(26.dp)
                        .padding(end = 8.dp)
                )
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    repeat(units) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (different) condo.accent else ChampagneLight.copy(alpha = 0.38f))
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 26.dp)
                .height(2.dp)
                .background(condo.onHeroMuted.copy(alpha = 0.5f))
        )
        Text(
            "Térreo",
            style = MaterialTheme.typography.labelSmall,
            color = condo.onHeroMuted,
            modifier = Modifier.padding(start = 26.dp, top = 4.dp)
        )
    }
}

@Composable
private fun FloorDialog(tower: Tower, floor: Int, onApply: (Int) -> Unit, onDismiss: () -> Unit) {
    var units by remember(tower.id, floor) { mutableIntStateOf(tower.unitsOn(floor)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background,
        title = { Text("${floor}º andar · ${tower.name}", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column {
                Text("Quantos apartamentos tem este andar? O padrão do bloco é ${tower.unitsPerFloor}.")
                Spacer(modifier = Modifier.height(16.dp))
                Stepper("Aptos neste andar", units, 1, MAX_UNITS_PER_FLOOR, { units = it }, Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(units) }) { Text("Aplicar") }
        },
        dismissButton = {
            TextButton(onClick = { onApply(tower.unitsPerFloor) }) {
                Text("Usar padrão", color = MaterialTheme.colorScheme.onSurface)
            }
        }
    )
}
