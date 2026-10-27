package com.example.condo_mini.ui.screens.reservations

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.OutdoorGrill
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.condo_mini.domain.model.CommonArea
import com.example.condo_mini.domain.model.Reservation
import com.example.condo_mini.domain.model.ReservationPeriod
import com.example.condo_mini.domain.model.ReservationStatus
import com.example.condo_mini.ui.components.CondoCard
import com.example.condo_mini.ui.components.Eyebrow
import com.example.condo_mini.ui.components.LoadingState
import com.example.condo_mini.ui.components.PillChip
import com.example.condo_mini.ui.components.PrimaryButton
import com.example.condo_mini.ui.components.SectionHeader
import com.example.condo_mini.ui.components.StatusChip
import com.example.condo_mini.ui.components.StatusTone
import com.example.condo_mini.ui.theme.Ivory
import com.example.condo_mini.ui.util.formatDate
import com.example.condo_mini.ui.util.monthShort
import com.example.condo_mini.ui.util.toBrl
import com.example.condo_mini.ui.util.weekdayShort
import java.time.LocalDate

private const val BOOKING_WINDOW_DAYS = 30L

@Composable
fun ReservationsRoute(
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    viewModel: ReservationsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onMessageShown()
        }
    }

    ReservationsScreen(
        uiState = uiState,
        onAreaClick = viewModel::onAreaSelected,
        onCancelReservation = viewModel::onCancelReservation,
        modifier = modifier
    )

    uiState.booking?.let { booking ->
        BookingSheet(
            booking = booking,
            onDateSelected = viewModel::onDateSelected,
            onPeriodSelected = viewModel::onPeriodSelected,
            onConfirm = viewModel::onConfirmBooking,
            onDismiss = viewModel::onDismissBooking
        )
    }
}

@Composable
fun ReservationsScreen(
    uiState: ReservationsUiState,
    onAreaClick: (CommonArea) -> Unit,
    onCancelReservation: (Reservation) -> Unit,
    modifier: Modifier = Modifier
) {
    if (uiState.isLoading) {
        LoadingState(modifier)
        return
    }
    var toCancel by remember { mutableStateOf<Reservation?>(null) }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (uiState.myReservations.isNotEmpty()) {
            item {
                SectionHeader(
                    "Minhas reservas",
                    trailing = "${uiState.myReservations.size}",
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
            items(uiState.myReservations, key = { it.id }) { reservation ->
                MyReservationCard(reservation, onCancel = { toCancel = reservation })
            }
        }
        item { SectionHeader("Espaços do condomínio", modifier = Modifier.padding(horizontal = 4.dp)) }
        items(uiState.areas, key = { it.id }) { area ->
            AreaCard(area, onClick = { onAreaClick(area) })
        }
    }

    toCancel?.let { reservation ->
        AlertDialog(
            onDismissRequest = { toCancel = null },
            containerColor = MaterialTheme.colorScheme.background,
            title = { Text("Cancelar reserva?", style = MaterialTheme.typography.headlineSmall) },
            text = {
                Text("${reservation.area.name} em ${reservation.date.formatDate()} (${reservation.period.label}).")
            },
            confirmButton = {
                TextButton(onClick = {
                    onCancelReservation(reservation)
                    toCancel = null
                }) { Text("Cancelar reserva", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { toCancel = null }) {
                    Text("Manter", color = MaterialTheme.colorScheme.onSurface)
                }
            }
        )
    }
}

/** Capa com gradiente no lugar da foto do espaço (troque por imagens reais quando houver). */
@Composable
private fun AreaCard(area: CommonArea, onClick: () -> Unit) {
    val visual = area.visual()
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraLarge,
        color = visual.end,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(148.dp)
                .background(Brush.linearGradient(listOf(visual.start, visual.end)))
        ) {
            Icon(
                imageVector = visual.icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.09f),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = 24.dp)
                    .size(150.dp)
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(22.dp)
            ) {
                Eyebrow(
                    text = "Até ${area.capacity} pessoas · ${if (area.fee > 0) area.fee.toBrl() else "Cortesia"}",
                    color = Ivory.copy(alpha = 0.75f)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        area.name,
                        style = MaterialTheme.typography.headlineMedium,
                        color = Ivory,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = Ivory.copy(alpha = 0.85f),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    area.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = Ivory.copy(alpha = 0.7f),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun MyReservationCard(reservation: Reservation, onCancel: () -> Unit) {
    CondoCard(contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DateBlock(reservation.date)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(reservation.area.name, style = MaterialTheme.typography.titleLarge)
                Text(
                    "${reservation.period.label} · ${reservation.period.hours}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                StatusChip(text = reservation.status.label, tone = reservation.status.tone())
                reservation.rejectionReason?.let {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Motivo: $it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (reservation.status != ReservationStatus.REJECTED) {
                TextButton(onClick = onCancel) {
                    Text("Cancelar", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

internal fun ReservationStatus.tone(): StatusTone = when (this) {
    ReservationStatus.CONFIRMED -> StatusTone.SUCCESS
    ReservationStatus.PENDING -> StatusTone.WARNING
    ReservationStatus.REJECTED, ReservationStatus.CANCELED -> StatusTone.ERROR
}

@Composable
internal fun DateBlock(date: LocalDate) {
    Column(
        modifier = Modifier
            .width(58.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Eyebrow(date.monthShort(), color = MaterialTheme.colorScheme.onTertiaryContainer)
        Text(
            "${date.dayOfMonth}",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onTertiaryContainer
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookingSheet(
    booking: BookingState,
    onDateSelected: (LocalDate) -> Unit,
    onPeriodSelected: (ReservationPeriod) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val today = remember { LocalDate.now() }
    val dates = remember(today) { (0 until BOOKING_WINDOW_DAYS).map { today.plusDays(it) } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                Eyebrow("Nova reserva")
                Spacer(modifier = Modifier.height(6.dp))
                Text(booking.area.name, style = MaterialTheme.typography.headlineLarge)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    booking.area.rules,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(24.dp))
                SectionHeader("Data", trailing = booking.date.formatDate())
            }
            LazyRow(
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(dates) { date ->
                    PillChip(
                        selected = date == booking.date,
                        onClick = { onDateSelected(date) },
                        modifier = Modifier.width(60.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(date.weekdayShort(), style = MaterialTheme.typography.labelSmall)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("${date.dayOfMonth}", style = MaterialTheme.typography.headlineSmall)
                        }
                    }
                }
            }

            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                Spacer(modifier = Modifier.height(12.dp))
                SectionHeader("Período")
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReservationPeriod.entries.forEach { period ->
                        val isBooked = period in booking.bookedPeriods
                        PillChip(
                            selected = period == booking.period,
                            enabled = !isBooked,
                            onClick = { onPeriodSelected(period) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(period.label, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    if (isBooked) "Indisponível" else period.hours,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }

                if (booking.area.fee > 0) {
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .background(MaterialTheme.colorScheme.tertiaryContainer)
                            .padding(16.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Taxa de uso", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Lançada no próximo boleto. Sujeita à aprovação do síndico.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(booking.area.fee.toBrl(), style = MaterialTheme.typography.titleLarge)
                    }
                }

                booking.error?.let {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                Spacer(modifier = Modifier.height(24.dp))
                PrimaryButton(
                    text = "Confirmar reserva",
                    onClick = onConfirm,
                    enabled = booking.period != null,
                    loading = booking.isSubmitting
                )
            }
        }
    }
}

private data class AreaVisual(val icon: ImageVector, val start: Color, val end: Color)

private fun CommonArea.visual(): AreaVisual = when (id) {
    "a1" -> AreaVisual(Icons.Default.Celebration, Color(0xFF3B3226), Color(0xFF1C1813))
    "a2" -> AreaVisual(Icons.Default.OutdoorGrill, Color(0xFF4A2F24), Color(0xFF1F1511))
    "a3" -> AreaVisual(Icons.Default.Restaurant, Color(0xFF2C3A33), Color(0xFF131A16))
    "a4" -> AreaVisual(Icons.Default.SportsSoccer, Color(0xFF243247), Color(0xFF111824))
    else -> AreaVisual(Icons.Default.MeetingRoom, Color(0xFF34303F), Color(0xFF16141C))
}
