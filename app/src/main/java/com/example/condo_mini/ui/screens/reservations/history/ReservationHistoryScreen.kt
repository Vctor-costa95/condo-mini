package com.example.condo_mini.ui.screens.reservations.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.condo_mini.domain.model.ReservationStatus
import com.example.condo_mini.ui.components.CondoCard
import com.example.condo_mini.ui.components.EmptyState
import com.example.condo_mini.ui.components.Eyebrow
import com.example.condo_mini.ui.components.IconBadge
import com.example.condo_mini.ui.components.LoadingState
import com.example.condo_mini.ui.components.PillChip
import com.example.condo_mini.ui.components.SectionHeader
import com.example.condo_mini.ui.components.StatusChip
import com.example.condo_mini.ui.components.condoTextFieldColors
import com.example.condo_mini.ui.screens.reservations.DateBlock
import com.example.condo_mini.ui.screens.reservations.tone
import com.example.condo_mini.ui.theme.CondominiTheme
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ReservationHistoryRoute(
    modifier: Modifier = Modifier,
    viewModel: ReservationHistoryViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ReservationHistoryScreen(
        uiState = uiState,
        onFilterSelected = viewModel::onFilterSelected,
        onUnitQueryChanged = viewModel::onUnitQueryChanged,
        modifier = modifier
    )
}

/** Tudo o que já foi pedido, de qualquer status e data, mês a mês. Só leitura. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReservationHistoryScreen(
    uiState: ReservationHistoryUiState,
    onFilterSelected: (HistoryFilter) -> Unit,
    onUnitQueryChanged: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (uiState.isLoading) {
        LoadingState(modifier)
        return
    }
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            // Quebra para a linha de baixo em vez de cortar o último filtro na borda da tela.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HistoryFilter.entries.forEach { option ->
                    PillChip(selected = option == uiState.filter, onClick = { onFilterSelected(option) }) {
                        Text(option.label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                    }
                }
            }
        }
        if (uiState.isManager) {
            item {
                OutlinedTextField(
                    value = uiState.unitQuery,
                    onValueChange = onUnitQueryChanged,
                    placeholder = { Text("Filtrar por apartamento") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    colors = condoTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        if (uiState.totalCount == 0) {
            item {
                EmptyState(
                    icon = Icons.Outlined.History,
                    title = "Nada por aqui",
                    message = if (uiState.filter == HistoryFilter.ALL && uiState.unitQuery.isBlank()) {
                        "Reservas, mudanças e reparos aparecem aqui depois de pedidos."
                    } else {
                        "Nenhum pedido com esse filtro."
                    },
                    modifier = Modifier.height(280.dp)
                )
            }
        }
        uiState.months.forEach { (month, entries) ->
            item(key = "month-$month") {
                SectionHeader(
                    month.label(),
                    trailing = "${entries.size}",
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp)
                )
            }
            items(entries, key = { it.id }) { entry ->
                HistoryCard(entry, showUnit = uiState.isManager)
            }
        }
    }
}

/** Atalho no topo das telas de reservas (morador e síndico) para o Histórico. */
@Composable
fun HistoryLinkCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    CondoCard(modifier = modifier, contentPadding = PaddingValues(0.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBadge(icon = Icons.Outlined.History, size = 40.dp)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Histórico", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Confirmadas, canceladas e recusadas",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Ver histórico",
                tint = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun HistoryCard(entry: HistoryEntry, showUnit: Boolean) {
    CondoCard(contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DateBlock(entry.date)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                if (showUnit) Eyebrow(entry.unit, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    entry.title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    entry.timeLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (showUnit && entry.residentName.isNotBlank()) {
                    Text(
                        "Solicitado por ${entry.residentName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                StatusChip(text = entry.statusLabel(), tone = entry.status.tone())
                entry.reason?.let {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Motivo: $it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Cancelada com motivo foi o síndico. Sem motivo pode ter sido o morador ou a exclusão do espaço,
 * então fica só "Cancelada".
 */
private fun HistoryEntry.statusLabel(): String =
    if (status == ReservationStatus.CANCELED && reason != null) "Cancelada pelo síndico" else status.label

private val monthFormatter = DateTimeFormatter.ofPattern("MMMM 'de' yyyy", Locale.forLanguageTag("pt-BR"))

/** "Novembro de 2026" */
private fun YearMonth.label(): String = format(monthFormatter).replaceFirstChar { it.uppercase() }

private fun previewState(manager: Boolean): ReservationHistoryUiState {
    val today = LocalDate.now()
    val entries = listOf(
        HistoryEntry("1", today.plusDays(3), "Salão de festas", "Tarde · 13h às 17h", "Apto 101 - Bloco A", "Ana Ribeiro", ReservationStatus.CONFIRMED, null, 13),
        HistoryEntry("2", today.plusDays(1), "Mudança", "Manhã · 08h às 12h · Mudanças X", "Apto 101 - Bloco A", "Ana Ribeiro", ReservationStatus.CANCELED, "Vazamento no elevador", 8),
        HistoryEntry("3", today.minusDays(2), "Churrasqueira", "Dia inteiro", "Apto 203 - Bloco A", "Fernanda Lopes", ReservationStatus.REJECTED, "Data reservada para manutenção", 0),
        HistoryEntry("4", today.minusDays(40), "Reparo · Internet", "Tarde · 13h às 17h", "Apto 101 - Bloco A", "Ana Ribeiro", ReservationStatus.CANCELED, null, 13)
    )
    return ReservationHistoryUiState(
        isLoading = false,
        isManager = manager,
        months = entries.groupBy { YearMonth.from(it.date) }.toList(),
        totalCount = entries.size
    )
}

@Preview(name = "Morador", showBackground = true)
@Preview(name = "Compacto + fonte 150%", showBackground = true, widthDp = 320, heightDp = 640, fontScale = 1.5f)
@Composable
private fun ReservationHistoryResidentPreview() {
    CondominiTheme {
        ReservationHistoryScreen(previewState(manager = false), onFilterSelected = {}, onUnitQueryChanged = {})
    }
}

@Preview(name = "Síndico", showBackground = true)
@Composable
private fun ReservationHistoryManagerPreview() {
    CondominiTheme {
        ReservationHistoryScreen(previewState(manager = true), onFilterSelected = {}, onUnitQueryChanged = {})
    }
}
