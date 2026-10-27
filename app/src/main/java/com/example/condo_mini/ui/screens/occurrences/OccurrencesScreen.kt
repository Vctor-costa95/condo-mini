package com.example.condo_mini.ui.screens.occurrences

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.condo_mini.domain.model.Occurrence
import com.example.condo_mini.domain.model.OccurrenceCategory
import com.example.condo_mini.domain.model.OccurrenceStatus
import com.example.condo_mini.ui.components.CondoCard
import com.example.condo_mini.ui.components.EmptyState
import com.example.condo_mini.ui.components.Eyebrow
import com.example.condo_mini.ui.components.LoadingState
import com.example.condo_mini.ui.components.PhotoAttachmentRow
import com.example.condo_mini.ui.components.PhotoStrip
import com.example.condo_mini.ui.components.PillChip
import com.example.condo_mini.ui.components.PrimaryButton
import com.example.condo_mini.ui.components.SectionHeader
import com.example.condo_mini.ui.components.StatusChip
import com.example.condo_mini.ui.components.StatusTone
import com.example.condo_mini.ui.components.condoTextFieldColors
import com.example.condo_mini.ui.screens.main.CondoSnackbarHost
import com.example.condo_mini.ui.util.formatDateTime

@Composable
fun OccurrencesRoute(
    modifier: Modifier = Modifier,
    viewModel: OccurrencesViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // Snackbar local para que o FAB suba junto com ela.
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onMessageShown()
        }
    }

    OccurrencesScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onNewOccurrence = viewModel::onNewOccurrence,
        onReply = viewModel::onReply,
        modifier = modifier
    )

    uiState.form?.let { form ->
        NewOccurrenceSheet(
            form = form,
            onCategoryChanged = viewModel::onCategoryChanged,
            onTitleChanged = viewModel::onTitleChanged,
            onDescriptionChanged = viewModel::onDescriptionChanged,
            onPhotoAdded = viewModel::onPhotoAdded,
            onPhotoRemoved = viewModel::onPhotoRemoved,
            onSubmit = viewModel::onSubmit,
            onDismiss = viewModel::onDismissForm
        )
    }
}

@Composable
fun OccurrencesScreen(
    uiState: OccurrencesUiState,
    snackbarHostState: SnackbarHostState,
    onNewOccurrence: () -> Unit,
    onReply: (Occurrence, String, List<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        // Os insets já foram aplicados pelo Scaffold da MainScreen.
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { CondoSnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewOccurrence,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Registrar", style = MaterialTheme.typography.labelLarge) },
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
                uiState.occurrences.isEmpty() -> EmptyState(
                    icon = Icons.AutoMirrored.Filled.MenuBook,
                    title = "Livro em branco",
                    message = "Registre problemas, reclamações ou sugestões para a administração."
                )

                else -> LazyColumn(
                    // Espaço extra no fim para o FAB não cobrir o último item.
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 104.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(
                            "Registros da sua unidade no livro oficial do condomínio.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                        )
                    }
                    items(uiState.occurrences, key = { it.id }) { occurrence ->
                        OccurrenceCard(occurrence, onReply = { text, photos -> onReply(occurrence, text, photos) })
                    }
                }
            }
        }
    }
}

@Composable
private fun OccurrenceCard(occurrence: Occurrence, onReply: (String, List<String>) -> Unit) {
    val needsInfo = occurrence.status == OccurrenceStatus.NEEDS_INFO
    // Pedido de detalhes já abre expandido para o morador ver e responder.
    var expanded by rememberSaveable(occurrence.id, needsInfo) { mutableStateOf(needsInfo) }
    CondoCard(onClick = { expanded = !expanded }, modifier = Modifier.animateContentSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Eyebrow(
                "${occurrence.protocol} · ${occurrence.category.label}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            StatusChip(occurrence.status.label, occurrence.status.tone())
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(occurrence.title, style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            occurrence.description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (expanded) Int.MAX_VALUE else 2,
            overflow = TextOverflow.Ellipsis
        )
        if (occurrence.photoUrls.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            PhotoStrip(occurrence.photoUrls, viewerTitle = occurrence.title)
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            occurrence.createdAt.formatDateTime(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.outline
        )
        if (expanded && occurrence.messages.isNotEmpty()) {
            Spacer(modifier = Modifier.height(14.dp))
            OccurrenceThread(occurrence)
        }
        if (expanded && needsInfo) {
            Spacer(modifier = Modifier.height(12.dp))
            ReplyField(placeholder = "Enviar mais detalhes…", onSend = onReply)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewOccurrenceSheet(
    form: OccurrenceForm,
    onCategoryChanged: (OccurrenceCategory) -> Unit,
    onTitleChanged: (String) -> Unit,
    onDescriptionChanged: (String) -> Unit,
    onPhotoAdded: (String) -> Unit,
    onPhotoRemoved: (String) -> Unit,
    onSubmit: () -> Unit,
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
            Eyebrow("Livro de ocorrências")
            Spacer(modifier = Modifier.height(6.dp))
            Text("Novo registro", style = MaterialTheme.typography.headlineLarge)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "Será analisado pela administração e você acompanha o andamento por aqui.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(20.dp))
            SectionHeader("Categoria")
            Spacer(modifier = Modifier.height(4.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OccurrenceCategory.entries.forEach { category ->
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
                value = form.description,
                onValueChange = onDescriptionChanged,
                label = { Text("Descreva o ocorrido") },
                supportingText = { Text("${form.description.length}/${OccurrencesViewModel.MAX_DESCRIPTION}") },
                minLines = 4,
                shape = MaterialTheme.shapes.medium,
                colors = condoTextFieldColors(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            PhotoAttachmentRow(
                photos = form.photoUrls,
                onAdd = onPhotoAdded,
                onRemove = onPhotoRemoved
            )
            Spacer(modifier = Modifier.height(8.dp))
            form.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(modifier = Modifier.height(16.dp))
            PrimaryButton(
                text = "Registrar ocorrência",
                onClick = onSubmit,
                loading = form.isSubmitting
            )
        }
    }
}
