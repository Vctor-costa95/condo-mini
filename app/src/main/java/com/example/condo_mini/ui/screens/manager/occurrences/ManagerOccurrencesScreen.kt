package com.example.condo_mini.ui.screens.manager.occurrences

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.condo_mini.domain.model.Occurrence
import com.example.condo_mini.domain.model.OccurrenceStatus
import com.example.condo_mini.ui.components.CondoCard
import com.example.condo_mini.ui.components.EmptyState
import com.example.condo_mini.ui.components.Eyebrow
import com.example.condo_mini.ui.components.LoadingState
import com.example.condo_mini.ui.components.PhotoStrip
import com.example.condo_mini.ui.components.PillChip
import com.example.condo_mini.ui.components.SectionHeader
import com.example.condo_mini.ui.components.StatusChip
import com.example.condo_mini.ui.screens.occurrences.OccurrenceThread
import com.example.condo_mini.ui.screens.occurrences.ReplyField
import com.example.condo_mini.ui.screens.occurrences.tone
import com.example.condo_mini.ui.util.formatDateTime
import com.example.condo_mini.ui.util.relative

@Composable
fun ManagerOccurrencesRoute(
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    viewModel: ManagerOccurrencesViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onMessageShown()
        }
    }

    ManagerOccurrencesScreen(
        uiState = uiState,
        onFilterSelected = viewModel::onFilterSelected,
        onOpen = viewModel::onOpen,
        modifier = modifier
    )

    uiState.selected?.let { occurrence ->
        OccurrenceDetailSheet(
            occurrence = occurrence,
            sending = uiState.isSending,
            onSend = viewModel::onSend,
            onDismiss = viewModel::onClose
        )
    }
}

@Composable
fun ManagerOccurrencesScreen(
    uiState: ManagerOccurrencesUiState,
    onFilterSelected: (OccurrenceFilter) -> Unit,
    onOpen: (Occurrence) -> Unit,
    modifier: Modifier = Modifier
) {
    if (uiState.isLoading) {
        LoadingState(modifier)
        return
    }
    Column(modifier = modifier.fillMaxSize()) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(OccurrenceFilter.entries) { filter ->
                PillChip(selected = uiState.filter == filter, onClick = { onFilterSelected(filter) }) {
                    Text(
                        "${filter.label} · ${uiState.counts[filter] ?: 0}",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
        if (uiState.occurrences.isEmpty()) {
            EmptyState(
                icon = Icons.AutoMirrored.Filled.MenuBook,
                title = "Nada por aqui",
                message = when (uiState.filter) {
                    OccurrenceFilter.TO_HANDLE -> "Nenhuma ocorrência aguardando sua resposta."
                    OccurrenceFilter.WAITING_RESIDENT -> "Nenhum pedido de detalhes em aberto."
                    OccurrenceFilter.RESOLVED -> "Ainda não há ocorrências resolvidas."
                }
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(uiState.occurrences, key = { it.id }) { occurrence ->
                    ManagerOccurrenceCard(occurrence, onClick = { onOpen(occurrence) })
                }
            }
        }
    }
}

@Composable
private fun ManagerOccurrenceCard(occurrence: Occurrence, onClick: () -> Unit) {
    CondoCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Eyebrow(occurrence.unit, modifier = Modifier.weight(1f))
            StatusChip(occurrence.status.label, occurrence.status.tone())
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(occurrence.title, style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            occurrence.description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(12.dp))
        val replies = occurrence.messages.size
        val photos = occurrence.photoUrls.size + occurrence.messages.sumOf { it.photoUrls.size }
        Text(
            buildString {
                append("${occurrence.protocol} · ${occurrence.category.label} · ${occurrence.createdAt.relative()}")
                if (replies > 0) append(" · $replies ${if (replies == 1) "mensagem" else "mensagens"}")
                if (photos > 0) append(" · $photos ${if (photos == 1) "foto" else "fotos"}")
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OccurrenceDetailSheet(
    occurrence: Occurrence,
    sending: Boolean,
    onSend: (ManagerAction, String, List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var action by rememberSaveable(occurrence.id) { mutableStateOf(ManagerAction.RESPOND) }
    val resolved = occurrence.status == OccurrenceStatus.RESOLVED

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
        ) {
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Eyebrow("${occurrence.protocol} · ${occurrence.category.label}", modifier = Modifier.weight(1f))
                    StatusChip(occurrence.status.label, occurrence.status.tone())
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(occurrence.title, style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "${occurrence.authorName.ifBlank { "Morador" }} · ${occurrence.unit}",
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    "Registrada em ${occurrence.createdAt.formatDateTime()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(occurrence.description, style = MaterialTheme.typography.bodyLarge)
                if (occurrence.photoUrls.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    PhotoStrip(occurrence.photoUrls, viewerTitle = occurrence.title, size = 88.dp)
                }
                if (occurrence.messages.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(20.dp))
                    SectionHeader("Conversa")
                    Spacer(modifier = Modifier.height(4.dp))
                    OccurrenceThread(occurrence)
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                if (resolved) {
                    Text(
                        "Ocorrência encerrada. O morador já foi notificado.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ManagerAction.entries.forEach { option ->
                            PillChip(
                                selected = action == option,
                                onClick = { action = option },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(option.label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    ReplyField(
                        placeholder = action.placeholder,
                        sending = sending,
                        onSend = { text, photos -> onSend(action, text, photos) }
                    )
                }
            }
        }
    }
}
