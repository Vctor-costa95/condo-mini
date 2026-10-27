package com.example.condo_mini.ui.screens.admin.areas

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Deck
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.condo_mini.domain.model.CommonArea
import com.example.condo_mini.ui.components.AreaCoverCard
import com.example.condo_mini.ui.components.AreaIcon
import com.example.condo_mini.ui.components.EmptyState
import com.example.condo_mini.ui.components.Eyebrow
import com.example.condo_mini.ui.components.LoadingState
import com.example.condo_mini.ui.components.PillChip
import com.example.condo_mini.ui.components.PrimaryButton
import com.example.condo_mini.ui.components.SectionHeader
import com.example.condo_mini.ui.components.condoTextFieldColors
import com.example.condo_mini.ui.screens.main.CondoSnackbarHost
import com.example.condo_mini.ui.theme.Ivory

private val closingReasons = listOf("Em manutenção", "Em reforma", "Limpeza programada", "Dedetização")

@Composable
fun AdminAreasRoute(
    modifier: Modifier = Modifier,
    viewModel: AdminAreasViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var toClose by remember { mutableStateOf<CommonArea?>(null) }

    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onMessageShown()
        }
    }

    AdminAreasScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onNewArea = viewModel::onNewArea,
        onEditArea = viewModel::onEditArea,
        onCloseArea = { toClose = it },
        onReopenArea = viewModel::onReopenArea,
        modifier = modifier
    )

    uiState.form?.let { form ->
        AreaFormSheet(
            form = form,
            upcoming = uiState.upcoming[form.id] ?: 0,
            onChange = viewModel::onFormChanged,
            onSave = viewModel::onSave,
            onDelete = viewModel::onDelete,
            onDismiss = viewModel::onDismissForm
        )
    }

    toClose?.let { area ->
        CloseAreaDialog(
            area = area,
            onConfirm = { reason ->
                viewModel.onCloseArea(area, reason)
                toClose = null
            },
            onDismiss = { toClose = null }
        )
    }
}

@Composable
fun AdminAreasScreen(
    uiState: AdminAreasUiState,
    snackbarHostState: SnackbarHostState,
    onNewArea: () -> Unit,
    onEditArea: (CommonArea) -> Unit,
    onCloseArea: (CommonArea) -> Unit,
    onReopenArea: (CommonArea) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { CondoSnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewArea,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Novo espaço", style = MaterialTheme.typography.labelLarge) },
                shape = MaterialTheme.shapes.medium,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 2.dp)
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when {
                uiState.isLoading -> LoadingState()
                uiState.areas.isEmpty() -> EmptyState(
                    icon = Icons.Default.Deck,
                    title = "Nenhum espaço",
                    message = "Crie o primeiro espaço para os moradores reservarem."
                )

                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 104.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    item {
                        Text(
                            "É assim que os moradores veem os espaços. Toque em um para editar.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                        )
                    }
                    items(uiState.areas, key = { it.id }) { area ->
                        Column {
                            AreaCoverCard(area, onClick = { onEditArea(area) })
                            AreaAdminBar(
                                area = area,
                                upcoming = uiState.upcoming[area.id] ?: 0,
                                onClose = { onCloseArea(area) },
                                onReopen = { onReopenArea(area) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AreaAdminBar(area: CommonArea, upcoming: Int, onClose: () -> Unit, onReopen: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            when (upcoming) {
                0 -> "Sem reservas futuras"
                1 -> "1 reserva futura"
                else -> "$upcoming reservas futuras"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        if (area.isClosed) {
            TextButton(onClick = onReopen) {
                Icon(Icons.Outlined.LockOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Liberar", style = MaterialTheme.typography.labelLarge)
            }
        } else {
            TextButton(onClick = onClose) {
                Icon(
                    Icons.Outlined.Block,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Interditar", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AreaFormSheet(
    form: AreaForm,
    upcoming: Int,
    onChange: ((AreaForm) -> AreaForm) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var confirmDelete by remember { mutableStateOf(false) }

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
                .imePadding()
                .padding(bottom = 16.dp)
        ) {
            Eyebrow("Espaços do condomínio")
            Spacer(modifier = Modifier.height(6.dp))
            Text(if (form.isNew) "Novo espaço" else "Editar espaço", style = MaterialTheme.typography.headlineLarge)
            Spacer(modifier = Modifier.height(16.dp))

            // Prévia ao vivo: o morador verá exatamente esta capa.
            AreaCoverCard(form.preview())
            Spacer(modifier = Modifier.height(8.dp))

            SectionHeader("Ícone", trailing = AreaIcon.fromKey(form.iconKey).label)
            Spacer(modifier = Modifier.height(4.dp))
            IconPicker(selectedKey = form.iconKey, onSelect = { key -> onChange { it.copy(iconKey = key) } })

            Spacer(modifier = Modifier.height(16.dp))
            SectionHeader("Informações")
            OutlinedTextField(
                value = form.name,
                onValueChange = { value -> onChange { it.copy(name = value.take(40)) } },
                label = { Text("Nome do espaço") },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                colors = condoTextFieldColors(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = form.description,
                onValueChange = { value -> onChange { it.copy(description = value.take(120)) } },
                label = { Text("Descrição curta") },
                shape = MaterialTheme.shapes.medium,
                colors = condoTextFieldColors(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = form.capacity,
                    onValueChange = { value -> onChange { it.copy(capacity = value.filter(Char::isDigit).take(4)) } },
                    label = { Text("Capacidade") },
                    suffix = { Text("pessoas") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    colors = condoTextFieldColors(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = form.fee,
                    onValueChange = { value -> onChange { it.copy(fee = value.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(8)) } },
                    label = { Text("Taxa") },
                    prefix = { Text("R$ ") },
                    placeholder = { Text("Grátis") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    colors = condoTextFieldColors(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                "Espaços com taxa precisam da aprovação do síndico.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp, start = 4.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = form.rules,
                onValueChange = { value -> onChange { it.copy(rules = value.take(300)) } },
                label = { Text("Regras de uso") },
                minLines = 3,
                shape = MaterialTheme.shapes.medium,
                colors = condoTextFieldColors(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth()
            )

            form.error?.let {
                Spacer(modifier = Modifier.height(12.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(modifier = Modifier.height(20.dp))
            PrimaryButton(
                text = if (form.isNew) "Criar espaço" else "Salvar alterações",
                onClick = onSave,
                loading = form.isSaving
            )
            if (!form.isNew) {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Excluir espaço", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = MaterialTheme.colorScheme.background,
            title = { Text("Excluir ${form.name}?", style = MaterialTheme.typography.headlineSmall) },
            text = {
                Text(
                    when (upcoming) {
                        0 -> "O espaço deixa de aparecer para os moradores."
                        1 -> "O espaço deixa de aparecer e 1 reserva futura será cancelada."
                        else -> "O espaço deixa de aparecer e $upcoming reservas futuras serão canceladas."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text("Excluir", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("Manter", color = MaterialTheme.colorScheme.onSurface)
                }
            }
        )
    }
}

/** Grade com todos os ícones de [AreaIcon]; o escolhido ganha o tom da própria capa. */
@Composable
private fun IconPicker(selectedKey: String, onSelect: (String) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = 3
    ) {
        AreaIcon.entries.forEach { option ->
            val selected = option.key == selectedKey
            Surface(
                onClick = { onSelect(option.key) },
                shape = MaterialTheme.shapes.medium,
                color = if (selected) option.end else MaterialTheme.colorScheme.surfaceContainerLow,
                border = if (selected) {
                    BorderStroke(1.5.dp, MaterialTheme.colorScheme.tertiary)
                } else {
                    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                },
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        option.icon,
                        contentDescription = null,
                        tint = if (selected) Ivory else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        option.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) Ivory else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CloseAreaDialog(area: CommonArea, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var reason by remember { mutableStateOf(closingReasons.first()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background,
        title = { Text("Interditar ${area.name}?", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column {
                Text("Novas reservas ficam bloqueadas. As já marcadas continuam valendo.")
                Spacer(modifier = Modifier.height(12.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    closingReasons.forEach { option ->
                        PillChip(selected = reason == option, onClick = { reason = option }) {
                            Text(option, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it.take(60) },
                    label = { Text("Motivo exibido ao morador") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    colors = condoTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(reason) }) {
                Text("Interditar", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = MaterialTheme.colorScheme.onSurface)
            }
        }
    )
}
