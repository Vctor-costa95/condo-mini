package com.example.condo_mini.ui.screens.reservations

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Handyman
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.condo_mini.domain.model.CommonArea
import com.example.condo_mini.domain.model.Reservation
import com.example.condo_mini.domain.model.ReservationPeriod
import com.example.condo_mini.domain.model.ReservationStatus
import com.example.condo_mini.domain.model.RepairKind
import com.example.condo_mini.domain.model.SERVICE_PERIODS
import com.example.condo_mini.domain.model.ServiceBooking
import com.example.condo_mini.domain.model.ServiceType
import com.example.condo_mini.ui.screens.reservations.history.HistoryLinkCard
import com.example.condo_mini.ui.components.AreaCoverCard
import com.example.condo_mini.ui.components.CondoCard
import com.example.condo_mini.ui.components.Eyebrow
import com.example.condo_mini.ui.components.LoadingState
import com.example.condo_mini.ui.components.PillChip
import com.example.condo_mini.ui.components.PrimaryButton
import com.example.condo_mini.ui.components.SectionHeader
import com.example.condo_mini.ui.components.StatusChip
import com.example.condo_mini.ui.components.StatusTone
import com.example.condo_mini.ui.components.condoTextFieldColors
import com.example.condo_mini.ui.util.formatDate
import com.example.condo_mini.ui.util.monthShort
import com.example.condo_mini.ui.util.toBrl
import com.example.condo_mini.ui.util.weekdayShort
import java.time.LocalDate

private const val BOOKING_WINDOW_DAYS = 30L

@Composable
fun ReservationsRoute(
    snackbarHostState: SnackbarHostState,
    onHistoryClick: () -> Unit,
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
        onNewService = viewModel::onNewService,
        onCancelService = viewModel::onCancelService,
        onHistoryClick = onHistoryClick,
        modifier = modifier
    )

    uiState.booking?.let { booking ->
        BookingSheet(
            booking = booking,
            onDateSelected = viewModel::onDateSelected,
            onPeriodSelected = viewModel::onPeriodSelected,
            onStartHourSelected = viewModel::onStartHourSelected,
            onDurationSelected = viewModel::onDurationSelected,
            onConfirm = viewModel::onConfirmBooking,
            onDismiss = viewModel::onDismissBooking
        )
    }

    uiState.serviceRequest?.let { request ->
        ServiceRequestSheet(
            request = request,
            onDateSelected = viewModel::onServiceDateSelected,
            onChange = viewModel::onServiceChanged,
            onConfirm = viewModel::onConfirmService,
            onDismiss = viewModel::onDismissService
        )
    }
}

@Composable
fun ReservationsScreen(
    uiState: ReservationsUiState,
    onAreaClick: (CommonArea) -> Unit,
    onCancelReservation: (Reservation) -> Unit,
    onNewService: (ServiceType) -> Unit,
    onCancelService: (ServiceBooking) -> Unit,
    onHistoryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (uiState.isLoading) {
        LoadingState(modifier)
        return
    }
    var toCancel by remember { mutableStateOf<Reservation?>(null) }
    var serviceToCancel by remember { mutableStateOf<ServiceBooking?>(null) }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { HistoryLinkCard(onClick = onHistoryClick) }
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
        // Mudança e reparo não são espaços: ficam num bloco próprio, antes da lista de áreas.
        item {
            SectionHeader(
                "Mudanças e reparos",
                trailing = uiState.myServices.size.takeIf { it > 0 }?.toString(),
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
        item { ServiceShortcuts(onNewService) }
        items(uiState.myServices, key = { it.id }) { booking ->
            MyServiceCard(booking, onCancel = { serviceToCancel = booking })
        }
        item { SectionHeader("Espaços do condomínio", modifier = Modifier.padding(horizontal = 4.dp)) }
        items(uiState.areas, key = { it.id }) { area ->
            AreaCoverCard(area, onClick = if (area.isClosed) null else ({ onAreaClick(area) }))
        }
    }

    toCancel?.let { reservation ->
        AlertDialog(
            onDismissRequest = { toCancel = null },
            containerColor = MaterialTheme.colorScheme.background,
            title = { Text("Cancelar reserva?", style = MaterialTheme.typography.headlineSmall) },
            text = {
                Text("${reservation.area.name} em ${reservation.date.formatDate()} (${reservation.shortLabel}).")
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

    serviceToCancel?.let { booking ->
        AlertDialog(
            onDismissRequest = { serviceToCancel = null },
            containerColor = MaterialTheme.colorScheme.background,
            title = { Text("Cancelar agendamento?", style = MaterialTheme.typography.headlineSmall) },
            text = { Text("${booking.title} em ${booking.date.formatDate()} (${booking.period.label}).") },
            confirmButton = {
                TextButton(onClick = {
                    onCancelService(booking)
                    serviceToCancel = null
                }) { Text("Cancelar agendamento", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { serviceToCancel = null }) {
                    Text("Manter", color = MaterialTheme.colorScheme.onSurface)
                }
            }
        )
    }
}

/** Dois atalhos lado a lado: mudança e reparo. */
@Composable
private fun ServiceShortcuts(onNewService: (ServiceType) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ServiceShortcut(
            icon = Icons.Outlined.LocalShipping,
            title = "Mudança",
            caption = "Uma por manhã e uma por tarde",
            onClick = { onNewService(ServiceType.MOVE) },
            modifier = Modifier.weight(1f)
        )
        ServiceShortcut(
            icon = Icons.Outlined.Handyman,
            title = "Reparo",
            caption = "Internet, elétrica, hidráulica",
            onClick = { onNewService(ServiceType.REPAIR) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ServiceShortcut(
    icon: ImageVector,
    title: String,
    caption: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    CondoCard(onClick = onClick, contentPadding = PaddingValues(16.dp), modifier = modifier) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.tertiaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(
            caption,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            minLines = 2
        )
    }
}

@Composable
private fun MyServiceCard(booking: ServiceBooking, onCancel: () -> Unit) {
    CondoCard(contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DateBlock(booking.date)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(booking.title, style = MaterialTheme.typography.titleLarge)
                Text(
                    listOfNotNull("${booking.period.label} · ${booking.period.hours}", booking.provider.ifBlank { null })
                        .joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                StatusChip(text = booking.status.label, tone = booking.status.tone())
                booking.rejectionReason?.let {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Motivo: $it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            // Recusados e cancelados pelo síndico ficam na lista só para o morador ver o motivo.
            if (booking.status == ReservationStatus.PENDING || booking.status == ReservationStatus.CONFIRMED) {
                TextButton(onClick = onCancel) {
                    Text("Cancelar", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServiceRequestSheet(
    request: ServiceRequestState,
    onDateSelected: (LocalDate) -> Unit,
    onChange: ((ServiceRequestState) -> ServiceRequestState) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // A partir de amanhã: o síndico precisa de tempo para aprovar e avisar a portaria.
    val tomorrow = remember { LocalDate.now().plusDays(1) }
    val dates = remember(tomorrow) { (0 until BOOKING_WINDOW_DAYS).map { tomorrow.plusDays(it) } }
    val isMove = request.type == ServiceType.MOVE

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                Eyebrow("Agendar")
                Spacer(modifier = Modifier.height(6.dp))
                Text(if (isMove) "Mudança" else "Reparo ou instalação", style = MaterialTheme.typography.headlineLarge)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    if (isMove) {
                        "Uma mudança por período. Elevador de serviço reservado e portaria avisada."
                    } else {
                        "A portaria acompanha a entrada do técnico até o apartamento."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(20.dp))
                SectionHeader("Data", trailing = request.date.formatDate())
            }
            LazyRow(
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(dates) { date ->
                    PillChip(
                        selected = date == request.date,
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
                Spacer(modifier = Modifier.height(8.dp))
                SectionHeader("Período")
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SERVICE_PERIODS.forEach { period ->
                        val isTaken = period in request.takenPeriods
                        PillChip(
                            selected = period == request.period,
                            enabled = !isTaken,
                            onClick = { onChange { it.copy(period = period) } },
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(period.label, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    if (isTaken) "Já tem mudança" else period.hours,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }

                if (!isMove) {
                    Spacer(modifier = Modifier.height(8.dp))
                    SectionHeader("O que será feito")
                    Spacer(modifier = Modifier.height(4.dp))
                    // Duas por linha: "Hidráulica" não cabe inteira com quatro lado a lado.
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        RepairKind.entries.chunked(2).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { kind ->
                                    PillChip(
                                        selected = kind == request.repairKind,
                                        onClick = { onChange { it.copy(repairKind = kind) } },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(kind.label, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = request.provider,
                    onValueChange = { value -> onChange { it.copy(provider = value.take(60)) } },
                    label = { Text(if (isMove) "Transportadora (opcional)" else "Empresa ou técnico (opcional)") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    colors = condoTextFieldColors(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.tertiaryContainer)
                        .padding(16.dp)
                ) {
                    Text(
                        "O síndico aprova o pedido. Você recebe a resposta aqui, em Mudanças e reparos.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }

                request.error?.let {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                Spacer(modifier = Modifier.height(20.dp))
                PrimaryButton(
                    text = "Enviar para aprovação",
                    onClick = onConfirm,
                    enabled = request.period != null && (isMove || request.repairKind != null),
                    loading = request.isSubmitting
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
                    reservation.timeLabel,
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
            if (reservation.status == ReservationStatus.PENDING || reservation.status == ReservationStatus.CONFIRMED) {
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
    onStartHourSelected: (Int) -> Unit,
    onDurationSelected: (Int) -> Unit,
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
                if (booking.area.isDaily) {
                    FullDayStatus(booking)
                } else if (booking.area.isHourly) {
                    HourPicker(booking, onStartHourSelected, onDurationSelected)
                } else {
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
                    enabled = booking.canConfirm,
                    loading = booking.isSubmitting
                )
            }
        }
    }
}

/** Espaço do dia inteiro: não há horário para escolher, só se a data está livre. */
@Composable
private fun FullDayStatus(booking: BookingState) {
    SectionHeader("Horário")
    Spacer(modifier = Modifier.height(4.dp))
    PillChip(
        selected = !booking.isDayTaken,
        enabled = !booking.isDayTaken,
        onClick = {},
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Dia inteiro", style = MaterialTheme.typography.titleSmall)
            Text(
                if (booking.isDayTaken) "Indisponível · já reservado nesta data" else "O espaço fica com você o dia todo",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

/**
 * Espaço por hora: o morador toca na hora de início (ocupadas e já passadas ficam
 * apagadas) e escolhe quantas horas, até o máximo do espaço ou a próxima reserva.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HourPicker(
    booking: BookingState,
    onStartHourSelected: (Int) -> Unit,
    onDurationSelected: (Int) -> Unit
) {
    val area = booking.area
    val available = booking.startHours.toSet()
    SectionHeader("Início", trailing = "Funciona ${area.openingHours}")
    Spacer(modifier = Modifier.height(4.dp))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = 5
    ) {
        (area.opensAt until area.closesAt).forEach { hour ->
            PillChip(
                selected = hour == booking.startHour,
                enabled = hour in available,
                onClick = { onStartHourSelected(hour) },
                modifier = Modifier.weight(1f)
            ) {
                Text("${hour}h", style = MaterialTheme.typography.titleSmall)
            }
        }
        // Completa a última linha para os botões manterem a mesma largura.
        repeat((5 - (area.closesAt - area.opensAt) % 5) % 5) { Spacer(modifier = Modifier.weight(1f)) }
    }
    if (available.isEmpty()) {
        Text(
            "Nenhum horário livre neste dia.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }

    val start = booking.startHour ?: return
    Spacer(modifier = Modifier.height(16.dp))
    SectionHeader("Duração", trailing = "Das ${start}h às ${start + booking.duration}h")
    Spacer(modifier = Modifier.height(4.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        (1..area.maxHours).forEach { hours ->
            PillChip(
                selected = hours == booking.duration,
                enabled = hours in booking.durations,
                onClick = { onDurationSelected(hours) },
                modifier = Modifier.weight(1f)
            ) {
                Text(if (hours == 1) "1 hora" else "$hours horas", style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}
