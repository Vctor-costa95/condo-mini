package com.example.condo_mini.ui.screens.admin.users

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.outlined.Search
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.condo_mini.domain.model.CondoUnit
import com.example.condo_mini.domain.model.User
import com.example.condo_mini.domain.model.UserRole
import com.example.condo_mini.ui.components.CondoCard
import com.example.condo_mini.ui.components.EmptyState
import com.example.condo_mini.ui.components.Eyebrow
import com.example.condo_mini.ui.components.InitialsAvatar
import com.example.condo_mini.ui.components.LoadingState
import com.example.condo_mini.ui.components.PillChip
import com.example.condo_mini.ui.components.PrimaryButton
import com.example.condo_mini.ui.components.SectionHeader
import com.example.condo_mini.ui.components.StatusChip
import com.example.condo_mini.ui.components.StatusTone
import com.example.condo_mini.ui.components.condoTextFieldColors
import com.example.condo_mini.ui.screens.main.CondoSnackbarHost

private val UserRole.plural: String
    get() = when (this) {
        UserRole.ADMIN -> "Administradores"
        UserRole.MANAGER -> "Síndicos"
        UserRole.DOORMAN -> "Porteiros"
        UserRole.RESIDENT -> "Moradores"
    }

// Ordem do filtro: do mais numeroso ao mais raro.
private val roleOrder = listOf(UserRole.RESIDENT, UserRole.MANAGER, UserRole.DOORMAN, UserRole.ADMIN)

@Composable
fun AdminUsersRoute(
    modifier: Modifier = Modifier,
    viewModel: AdminUsersViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onMessageShown()
        }
    }

    AdminUsersScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onRoleSelected = viewModel::onRoleSelected,
        onQueryChanged = viewModel::onQueryChanged,
        onNewUser = viewModel::onNewUser,
        onEditUser = viewModel::onEditUser,
        modifier = modifier
    )

    uiState.form?.let { form ->
        UserFormSheet(
            form = form,
            units = uiState.units,
            onChange = viewModel::onFormChanged,
            onSave = viewModel::onSave,
            onDelete = viewModel::onDelete,
            onDismiss = viewModel::onDismissForm
        )
    }
}

@Composable
fun AdminUsersScreen(
    uiState: AdminUsersUiState,
    snackbarHostState: SnackbarHostState,
    onRoleSelected: (UserRole?) -> Unit,
    onQueryChanged: (String) -> Unit,
    onNewUser: () -> Unit,
    onEditUser: (User) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { CondoSnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewUser,
                icon = { Icon(Icons.Default.PersonAdd, contentDescription = null) },
                text = { Text("Novo cadastro", style = MaterialTheme.typography.labelLarge) },
                shape = MaterialTheme.shapes.medium,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 2.dp)
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            if (uiState.isLoading) {
                LoadingState()
                return@Box
            }
            LazyColumn(
                contentPadding = PaddingValues(bottom = 104.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = uiState.query,
                        onValueChange = onQueryChanged,
                        placeholder = { Text("Buscar por nome, unidade ou e-mail") },
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        colors = condoTextFieldColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 4.dp)
                    )
                }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            PillChip(selected = uiState.role == null, onClick = { onRoleSelected(null) }) {
                                Text("Todos · ${uiState.counts[null] ?: 0}", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                        items(roleOrder) { role ->
                            PillChip(selected = uiState.role == role, onClick = { onRoleSelected(role) }) {
                                Text("${role.plural} · ${uiState.counts[role] ?: 0}", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
                if (uiState.withoutUnit > 0 && (uiState.role == null || uiState.role == UserRole.RESIDENT)) {
                    item {
                        Text(
                            if (uiState.withoutUnit == 1) {
                                "1 morador ainda sem unidade. Toque nele para escolher o apartamento."
                            } else {
                                "${uiState.withoutUnit} moradores ainda sem unidade. Toque em cada um para escolher o apartamento."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                        )
                    }
                }
                if (uiState.users.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Default.Groups,
                            title = "Ninguém encontrado",
                            message = "Ajuste a busca ou crie um novo cadastro.",
                            modifier = Modifier.height(320.dp)
                        )
                    }
                }
                items(uiState.users, key = { it.id }) { user ->
                    UserCard(user, onClick = { onEditUser(user) }, modifier = Modifier.padding(horizontal = 20.dp))
                }
            }
        }
    }
}

@Composable
private fun UserCard(user: User, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val missingUnit = user.role == UserRole.RESIDENT && user.unit.isBlank()
    CondoCard(onClick = onClick, contentPadding = PaddingValues(16.dp), modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            InitialsAvatar(user.name, size = 44.dp)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(user.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    user.unit.ifBlank { user.email.ifBlank { "Sem contato" } },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            if (missingUnit) {
                StatusChip("Sem unidade", StatusTone.WARNING)
            } else {
                StatusChip(user.role.label, if (user.role == UserRole.RESIDENT) StatusTone.NEUTRAL else StatusTone.ACCENT)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UserFormSheet(
    form: UserForm,
    units: List<CondoUnit>,
    onChange: ((UserForm) -> UserForm) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var confirmDelete by remember { mutableStateOf(false) }
    val blocks = remember(units) { units.map { it.block }.distinct() }

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
            Eyebrow(if (form.isNew) "Cadastros" else form.role.label)
            Spacer(modifier = Modifier.height(6.dp))
            Text(if (form.isNew) "Novo cadastro" else form.name.ifBlank { "Cadastro" }, style = MaterialTheme.typography.headlineLarge)
            Spacer(modifier = Modifier.height(16.dp))

            SectionHeader("Perfil de acesso")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                roleOrder.forEach { role ->
                    PillChip(selected = form.role == role, onClick = { onChange { it.copy(role = role) } }) {
                        Text(role.label, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = form.name,
                onValueChange = { value -> onChange { it.copy(name = value) } },
                label = { Text("Nome completo") },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                colors = condoTextFieldColors(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = form.email,
                onValueChange = { value -> onChange { it.copy(email = value) } },
                label = { Text("E-mail") },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                colors = condoTextFieldColors(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = form.phone,
                onValueChange = { value -> onChange { it.copy(phone = value) } },
                label = { Text("Telefone") },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                colors = condoTextFieldColors(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth()
            )

            if (form.needsUnit) {
                Spacer(modifier = Modifier.height(8.dp))
                SectionHeader("Unidade", trailing = form.unit.ifBlank { "Nenhuma" })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    blocks.forEach { block ->
                        PillChip(
                            selected = form.block == block,
                            onClick = { onChange { it.copy(block = block) } },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Bloco $block", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    maxItemsInEachRow = 4
                ) {
                    units.filter { it.block == form.block }.forEach { unit ->
                        PillChip(
                            selected = form.unit == unit.label,
                            onClick = { onChange { it.copy(unit = if (it.unit == unit.label) "" else unit.label) } },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(unit.number, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }

            form.error?.let {
                Spacer(modifier = Modifier.height(12.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(modifier = Modifier.height(20.dp))
            PrimaryButton(
                text = if (form.isNew) "Criar cadastro" else "Salvar alterações",
                onClick = onSave,
                loading = form.isSaving
            )
            if (!form.isNew) {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Excluir cadastro", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = MaterialTheme.colorScheme.background,
            title = { Text("Excluir cadastro?", style = MaterialTheme.typography.headlineSmall) },
            text = { Text("${form.name} perde o acesso ao app. Esta ação não pode ser desfeita.") },
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
