package com.example.condo_mini.ui.screens.manager.reservations

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.condo_mini.domain.model.Reservation
import com.example.condo_mini.ui.components.CondoCard
import com.example.condo_mini.ui.components.EmptyState
import com.example.condo_mini.ui.components.Eyebrow
import com.example.condo_mini.ui.components.LoadingState
import com.example.condo_mini.ui.components.SectionHeader
import com.example.condo_mini.ui.components.StatusChip
import com.example.condo_mini.ui.components.condoTextFieldColors
import com.example.condo_mini.ui.screens.reservations.DateBlock
import com.example.condo_mini.ui.screens.reservations.tone
import com.example.condo_mini.ui.util.toBrl

@Composable
fun ManagerReservationsRoute(
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    viewModel: ManagerReservationsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onMessageShown()
        }
    }

    ManagerReservationsScreen(
        uiState = uiState,
        onApprove = viewModel::onApprove,
        onReject = viewModel::onReject,
        modifier = modifier
    )
}

@Composable
fun ManagerReservationsScreen(
    uiState: ManagerReservationsUiState,
    onApprove: (Reservation) -> Unit,
    onReject: (Reservation, String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (uiState.isLoading) {
        LoadingState(modifier)
        return
    }
    var toReject by remember { mutableStateOf<Reservation?>(null) }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionHeader(
                "Aguardando aprovação",
                trailing = "${uiState.pending.size}",
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
        if (uiState.pending.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Outlined.EventAvailable,
                    title = "Tudo em dia",
                    message = "Nenhuma reserva aguardando sua aprovação.",
                    modifier = Modifier.height(260.dp)
                )
            }
        }
        items(uiState.pending, key = { it.id }) { reservation ->
            PendingReservationCard(
                reservation = reservation,
                processing = reservation.id in uiState.processingIds,
                onApprove = { onApprove(reservation) },
                onReject = { toReject = reservation }
            )
        }
        if (uiState.upcoming.isNotEmpty()) {
            item {
                SectionHeader(
                    "Próximas confirmadas",
                    trailing = "${uiState.upcoming.size}",
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 12.dp)
                )
            }
            items(uiState.upcoming, key = { it.id }) { ReservationSummaryCard(it) }
        }
    }

    toReject?.let { reservation ->
        RejectDialog(
            reservation = reservation,
            onConfirm = { reason ->
                onReject(reservation, reason)
                toReject = null
            },
            onDismiss = { toReject = null }
        )
    }
}

@Composable
private fun PendingReservationCard(
    reservation: Reservation,
    processing: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    CondoCard(contentPadding = PaddingValues(18.dp)) {
        ReservationHeader(reservation)
        if (reservation.area.fee > 0) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                "Taxa de ${reservation.area.fee.toBrl()} lançada no boleto da unidade após aprovação.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onReject,
                enabled = !processing,
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            ) {
                Text("Recusar", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
            }
            Button(
                onClick = onApprove,
                enabled = !processing,
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            ) {
                if (processing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Aprovar", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun ReservationSummaryCard(reservation: Reservation) {
    CondoCard(contentPadding = PaddingValues(18.dp)) {
        ReservationHeader(reservation)
    }
}

@Composable
private fun ReservationHeader(reservation: Reservation) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        DateBlock(reservation.date)
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Eyebrow(reservation.unit, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(reservation.area.name, style = MaterialTheme.typography.titleLarge)
            Text(
                "${reservation.period.label} · ${reservation.period.hours}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (reservation.residentName.isNotBlank()) {
                Text(
                    "Solicitado por ${reservation.residentName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        StatusChip(reservation.status.label.substringBefore(" "), reservation.status.tone())
    }
}

@Composable
private fun RejectDialog(
    reservation: Reservation,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var reason by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background,
        title = { Text("Recusar reserva", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "${reservation.area.name} · ${reservation.unit}. O morador verá o motivo informado.",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it.take(200) },
                    label = { Text("Motivo") },
                    minLines = 2,
                    shape = MaterialTheme.shapes.medium,
                    colors = condoTextFieldColors(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(reason) }, enabled = reason.isNotBlank()) {
                Text("Recusar", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Voltar", color = MaterialTheme.colorScheme.onSurface)
            }
        }
    )
}
