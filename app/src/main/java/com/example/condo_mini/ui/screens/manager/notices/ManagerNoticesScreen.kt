package com.example.condo_mini.ui.screens.manager.notices

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Campaign
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.condo_mini.domain.model.Notice
import com.example.condo_mini.domain.model.NoticeCategory
import com.example.condo_mini.ui.components.EmptyState
import com.example.condo_mini.ui.components.Eyebrow
import com.example.condo_mini.ui.components.LoadingState
import com.example.condo_mini.ui.components.PillChip
import com.example.condo_mini.ui.components.PrimaryButton
import com.example.condo_mini.ui.components.SectionHeader
import com.example.condo_mini.ui.components.condoTextFieldColors
import com.example.condo_mini.ui.screens.main.CondoSnackbarHost
import com.example.condo_mini.ui.screens.notices.FeaturedNotice
import com.example.condo_mini.ui.screens.notices.NoticeCard

@Composable
fun ManagerNoticesRoute(
    modifier: Modifier = Modifier,
    viewModel: ManagerNoticesViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onMessageShown()
        }
    }

    ManagerNoticesScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onNewNotice = viewModel::onNewNotice,
        onDelete = viewModel::onDelete,
        modifier = modifier
    )

    uiState.form?.let { form ->
        NewNoticeSheet(
            form = form,
            onCategoryChanged = viewModel::onCategoryChanged,
            onTitleChanged = viewModel::onTitleChanged,
            onBodyChanged = viewModel::onBodyChanged,
            onPinnedChanged = viewModel::onPinnedChanged,
            onPublish = viewModel::onPublish,
            onDismiss = viewModel::onDismissForm
        )
    }
}

@Composable
fun ManagerNoticesScreen(
    uiState: ManagerNoticesUiState,
    snackbarHostState: SnackbarHostState,
    onNewNotice: () -> Unit,
    onDelete: (Notice) -> Unit,
    modifier: Modifier = Modifier
) {
    var toDelete by remember { mutableStateOf<Notice?>(null) }
    val featured = uiState.pinned.firstOrNull()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { CondoSnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewNotice,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Novo aviso", style = MaterialTheme.typography.labelLarge) },
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
                featured == null && uiState.others.isEmpty() -> EmptyState(
                    icon = Icons.Default.Campaign,
                    title = "Mural vazio",
                    message = "Publique o primeiro comunicado para os moradores."
                )

                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 104.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(
                            "É assim que os moradores veem o mural. Toque em um aviso para gerenciá-lo.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                        )
                    }
                    featured?.let {
                        item(key = it.id) {
                            FeaturedNotice(it, footer = { DeleteAction(onHero = true) { toDelete = it } })
                        }
                    }
                    items(uiState.pinned.drop(1), key = { it.id }) {
                        NoticeCard(it, footer = { DeleteAction { toDelete = it } })
                    }
                    if (uiState.others.isNotEmpty()) {
                        item { SectionHeader("Recentes", modifier = Modifier.padding(horizontal = 4.dp)) }
                        items(uiState.others, key = { it.id }) {
                            NoticeCard(it, footer = { DeleteAction { toDelete = it } })
                        }
                    }
                }
            }
        }
    }

    toDelete?.let { notice ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            containerColor = MaterialTheme.colorScheme.background,
            title = { Text("Excluir aviso?", style = MaterialTheme.typography.headlineSmall) },
            text = { Text("“${notice.title}” deixará de aparecer para os moradores.") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(notice)
                    toDelete = null
                }) { Text("Excluir", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { toDelete = null }) {
                    Text("Manter", color = MaterialTheme.colorScheme.onSurface)
                }
            }
        )
    }
}

@Composable
private fun DeleteAction(onHero: Boolean = false, onClick: () -> Unit) {
    TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 0.dp)) {
        Text(
            "Excluir aviso",
            style = MaterialTheme.typography.labelLarge,
            color = if (onHero) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.error
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewNoticeSheet(
    form: NoticeForm,
    onCategoryChanged: (NoticeCategory) -> Unit,
    onTitleChanged: (String) -> Unit,
    onBodyChanged: (String) -> Unit,
    onPinnedChanged: (Boolean) -> Unit,
    onPublish: () -> Unit,
    onDismiss: () -> Unit
) {
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
                .imePadding()
                .padding(bottom = 16.dp)
        ) {
            Eyebrow("Mural do condomínio")
            Spacer(modifier = Modifier.height(6.dp))
            Text("Novo aviso", style = MaterialTheme.typography.headlineLarge)
            Spacer(modifier = Modifier.height(20.dp))

            SectionHeader("Categoria")
            Spacer(modifier = Modifier.height(4.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NoticeCategory.entries.forEach { category ->
                    PillChip(selected = form.category == category, onClick = { onCategoryChanged(category) }) {
                        Text(category.label, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            OutlinedTextField(
                value = form.title,
                onValueChange = onTitleChanged,
                label = { Text("Título") },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                colors = condoTextFieldColors(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = form.body,
                onValueChange = onBodyChanged,
                label = { Text("Mensagem para os moradores") },
                supportingText = { Text("${form.body.length}/${ManagerNoticesViewModel.MAX_BODY}") },
                minLines = 5,
                shape = MaterialTheme.shapes.medium,
                colors = condoTextFieldColors(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Fixar no topo do mural", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "O aviso fixado mais recente vira o destaque.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = form.pinned,
                    onCheckedChange = onPinnedChanged,
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        checkedThumbColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                )
            }
            form.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(modifier = Modifier.height(16.dp))
            PrimaryButton(text = "Publicar aviso", onClick = onPublish, loading = form.isSubmitting)
        }
    }
}
