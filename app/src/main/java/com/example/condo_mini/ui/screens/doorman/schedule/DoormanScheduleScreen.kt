package com.example.condo_mini.ui.screens.doorman.schedule

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Handyman
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.condo_mini.data.AppContainer
import com.example.condo_mini.domain.model.Reservation
import com.example.condo_mini.domain.model.ReservationStatus
import com.example.condo_mini.domain.model.ServiceBooking
import com.example.condo_mini.domain.model.ServiceType
import com.example.condo_mini.domain.repository.DirectoryRepository
import com.example.condo_mini.domain.repository.ReservationRepository
import com.example.condo_mini.domain.repository.ServiceBookingRepository
import com.example.condo_mini.domain.repository.SessionRepository
import com.example.condo_mini.ui.components.CondoCard
import com.example.condo_mini.ui.components.EmptyState
import com.example.condo_mini.ui.components.Eyebrow
import com.example.condo_mini.ui.components.HeroCard
import com.example.condo_mini.ui.components.InitialsAvatar
import com.example.condo_mini.ui.components.LoadingState
import com.example.condo_mini.ui.components.SectionHeader
import com.example.condo_mini.ui.components.areaIcon
import com.example.condo_mini.ui.components.condoTextFieldColors
import com.example.condo_mini.ui.screens.reservations.guestCountLabel
import com.example.condo_mini.ui.theme.CondoTheme
import com.example.condo_mini.ui.util.formatWeekday
import java.text.Normalizer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

/** Quantos dias à frente a portaria enxerga. */
private const val SCHEDULE_DAYS = 14L

/** Agendamento com o telefone de quem pediu, para a portaria avisar quando o técnico chegar. */
data class ScheduleEntry(val booking: ServiceBooking, val phone: String)

/** O que a portaria acompanha num dia: mudanças e reparos aprovados e reservas confirmadas. */
data class ScheduleDay(
    val date: LocalDate,
    val entries: List<ScheduleEntry>,
    val reservations: List<Reservation> = emptyList()
) {
    val isEmpty: Boolean get() = entries.isEmpty() && reservations.isEmpty()
}

data class DoormanScheduleUiState(
    val isLoading: Boolean = true,
    val today: ScheduleDay = ScheduleDay(LocalDate.now(), emptyList()),
    val upcoming: List<ScheduleDay> = emptyList()
)

@OptIn(ExperimentalCoroutinesApi::class)
class DoormanScheduleViewModel(
    sessionRepository: SessionRepository = AppContainer.sessionRepository,
    serviceBookingRepository: ServiceBookingRepository = AppContainer.serviceBookingRepository,
    reservationRepository: ReservationRepository = AppContainer.reservationRepository,
    directoryRepository: DirectoryRepository = AppContainer.directoryRepository
) : ViewModel() {

    val uiState: StateFlow<DoormanScheduleUiState> = sessionRepository.currentUser
        .flatMapLatest { user ->
            val condo = user?.condoId.orEmpty()
            combine(
                serviceBookingRepository.observeAllBookings(condo),
                reservationRepository.observeAllReservations(),
                directoryRepository.observeUsersOf(condo)
            ) { bookings, reservations, users ->
                val today = LocalDate.now()
                val last = today.plusDays(SCHEDULE_DAYS)
                fun inWindow(date: LocalDate) = !date.isBefore(today) && !date.isAfter(last)
                // A portaria só vê o que o síndico aprovou.
                val entries = bookings
                    .filter { it.status == ReservationStatus.CONFIRMED && inWindow(it.date) }
                    .map { booking ->
                        val phone = users.firstOrNull { it.name == booking.residentName && booking.unit in it.units }?.phone
                        ScheduleEntry(booking, phone.orEmpty())
                    }
                    .groupBy { it.booking.date }
                val confirmed = reservations
                    .filter { it.status == ReservationStatus.CONFIRMED && inWindow(it.date) }
                    .sortedBy { it.startHour }
                    .groupBy { it.date }
                val days = (entries.keys + confirmed.keys).sorted().map { date ->
                    ScheduleDay(date, entries[date].orEmpty(), confirmed[date].orEmpty())
                }
                DoormanScheduleUiState(
                    isLoading = false,
                    today = days.firstOrNull { it.date == today } ?: ScheduleDay(today, emptyList()),
                    upcoming = days.filter { it.date.isAfter(today) }
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DoormanScheduleUiState())
}

@Composable
fun DoormanScheduleRoute(
    modifier: Modifier = Modifier,
    viewModel: DoormanScheduleViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    DoormanScheduleScreen(
        uiState = uiState,
        onCall = { phone -> context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${phone.filter { it.isDigit() }}"))) },
        modifier = modifier
    )
}

@Composable
fun DoormanScheduleScreen(
    uiState: DoormanScheduleUiState,
    onCall: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (uiState.isLoading) {
        LoadingState(modifier)
        return
    }
    var openList by remember { mutableStateOf<Reservation?>(null) }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { TodaySummary(uiState) }
        val days = listOfNotNull(uiState.today.takeIf { !it.isEmpty }) + uiState.upcoming
        days.forEach { day ->
            item(key = day.date.toString()) {
                SectionHeader(
                    when (day.date) {
                        LocalDate.now() -> "Hoje"
                        LocalDate.now().plusDays(1) -> "Amanhã"
                        else -> day.date.formatWeekday()
                    },
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
            items(day.entries, key = { it.booking.id }) { ScheduleCard(it, onCall) }
            items(day.reservations, key = { it.id }) { reservation ->
                GuestListCard(reservation, onOpenList = { openList = reservation })
            }
        }
        if (days.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Outlined.EventAvailable,
                    title = "Agenda livre",
                    message = "Nenhuma mudança, reparo ou reserva para as próximas duas semanas.",
                    modifier = Modifier.height(280.dp)
                )
            }
        }
    }

    openList?.let { reservation ->
        GuestListSheet(reservation, onDismiss = { openList = null })
    }
}

@Composable
private fun TodaySummary(uiState: DoormanScheduleUiState) {
    val condo = CondoTheme.colors
    val today = uiState.today
    val moves = today.entries.count { it.booking.type == ServiceType.MOVE }
    val repairs = today.entries.size - moves
    val events = today.reservations.size
    val guests = today.reservations.sumOf { it.guests.size }
    val total = today.entries.size + events
    HeroCard {
        Eyebrow("Hoje na portaria", color = condo.accent)
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text("$total", style = MaterialTheme.typography.displaySmall, color = condo.onHero)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                if (total == 1) "agendamento" else "agendamentos",
                style = MaterialTheme.typography.bodyMedium,
                color = condo.onHeroMuted,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            if (today.isEmpty) {
                uiState.upcoming.firstOrNull()?.let { "Próximo: ${it.date.formatWeekday()}" } ?: "Nada marcado"
            } else {
                listOfNotNull(
                    moves.takeIf { it > 0 }?.let { if (it == 1) "1 mudança" else "$it mudanças" },
                    repairs.takeIf { it > 0 }?.let { if (it == 1) "1 reparo" else "$it reparos" },
                    events.takeIf { it > 0 }?.let { if (it == 1) "1 reserva" else "$it reservas" },
                    guests.takeIf { it > 0 }?.let { guestCountLabel(it) }
                ).joinToString(" · ")
            },
            style = MaterialTheme.typography.bodySmall,
            color = condo.onHeroMuted
        )
    }
}

/** Quantos nomes o cartão mostra; o resto fica na lista completa. */
private const val GUESTS_ON_CARD = 4

/**
 * Reserva confirmada, com a lista de convidados que o morador deixou. O horário e o espaço vêm
 * primeiro, como nos outros cartões; abaixo, os primeiros nomes e o acesso à lista com busca.
 */
@Composable
private fun GuestListCard(reservation: Reservation, onOpenList: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val guests = reservation.guests
    CondoCard(contentPadding = PaddingValues(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(
                modifier = Modifier
                    .width(64.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(scheme.tertiaryContainer)
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    reservation.area.areaIcon.icon,
                    contentDescription = null,
                    tint = scheme.onTertiaryContainer,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Eyebrow(reservation.period?.label ?: if (reservation.isFullDay) "Dia" else "${reservation.startHour}h", color = scheme.onTertiaryContainer)
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Eyebrow(reservation.unit, color = scheme.onSurfaceVariant)
                Text(reservation.area.name, style = MaterialTheme.typography.titleLarge)
                Text(
                    listOf(reservation.period?.hours ?: reservation.hoursLabel, reservation.residentName)
                        .filter { it.isNotBlank() }
                        .joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        HorizontalDivider(color = scheme.outlineVariant, modifier = Modifier.padding(end = 8.dp))
        if (guests.isEmpty()) {
            Text(
                "Sem lista de convidados. Na dúvida, confirme com o apartamento pelo interfone.",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp, bottom = 8.dp, end = 8.dp)
            )
            return@CondoCard
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp, end = 8.dp)) {
            Icon(Icons.Outlined.Groups, contentDescription = null, tint = scheme.tertiary, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(guestCountLabel(guests.size), style = MaterialTheme.typography.titleSmall)
        }
        Spacer(modifier = Modifier.height(6.dp))
        guests.sortedWith(String.CASE_INSENSITIVE_ORDER).take(GUESTS_ON_CARD).forEach { name ->
            Text(
                name,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(start = 26.dp, top = 2.dp, bottom = 2.dp)
            )
        }
        TextButton(onClick = onOpenList, modifier = Modifier.padding(start = 14.dp)) {
            Text(
                if (guests.size > GUESTS_ON_CARD) "Ver os ${guests.size} e buscar nome" else "Abrir lista",
                color = scheme.tertiary
            )
        }
    }
}

/** Lista completa em ordem alfabética, com busca: o porteiro digita o nome de quem chegou. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GuestListSheet(reservation: Reservation, onDismiss: () -> Unit) {
    var search by rememberSaveable { mutableStateOf("") }
    val sorted = remember(reservation.guests) { reservation.guests.sortedWith(String.CASE_INSENSITIVE_ORDER) }
    val shown = remember(sorted, search) {
        val term = search.trim().withoutAccents()
        if (term.isEmpty()) sorted else sorted.filter { it.withoutAccents().contains(term, ignoreCase = true) }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Eyebrow("${reservation.unit} · ${reservation.residentName}")
            Spacer(modifier = Modifier.height(6.dp))
            Text(reservation.area.name, style = MaterialTheme.typography.headlineLarge)
            Text(
                "${reservation.date.formatWeekday()} · ${reservation.timeLabel} · ${guestCountLabel(sorted.size)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text("Buscar nome") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                colors = condoTextFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 24.dp),
            modifier = Modifier.navigationBarsPadding().imePadding()
        ) {
            if (shown.isEmpty()) {
                item {
                    Text(
                        "Ninguém com esse nome na lista.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                }
            }
            items(shown) { name ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                    InitialsAvatar(name, size = 36.dp)
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(name, style = MaterialTheme.typography.bodyLarge)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(start = 50.dp))
            }
        }
    }
}

/** "José" acha "jose": o porteiro digita rápido, sem acento. */
private fun String.withoutAccents(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

@Composable
private fun ScheduleCard(entry: ScheduleEntry, onCall: (String) -> Unit) {
    val booking = entry.booking
    val isMove = booking.type == ServiceType.MOVE
    val scheme = MaterialTheme.colorScheme
    CondoCard(contentPadding = PaddingValues(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            // Período em destaque: é o que a portaria procura primeiro.
            Column(
                modifier = Modifier
                    .width(64.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(scheme.tertiaryContainer)
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    if (isMove) Icons.Outlined.LocalShipping else Icons.Outlined.Handyman,
                    contentDescription = null,
                    tint = scheme.onTertiaryContainer,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Eyebrow(booking.period.label, color = scheme.onTertiaryContainer)
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Eyebrow(booking.unit, color = scheme.onSurfaceVariant)
                Text(booking.title, style = MaterialTheme.typography.titleLarge)
                Text(
                    listOfNotNull(booking.period.hours, booking.provider.ifBlank { null }).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    if (isMove) {
                        "Reserve o elevador de serviço e proteja os corredores."
                    } else {
                        "Acompanhe o técnico até o apartamento."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.tertiary
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        HorizontalDivider(color = scheme.outlineVariant, modifier = Modifier.padding(end = 8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(booking.residentName, style = MaterialTheme.typography.titleSmall)
                Text(
                    entry.phone.ifBlank { "Sem telefone · avise pelo interfone" },
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
            }
            if (entry.phone.isNotBlank()) {
                IconButton(onClick = { onCall(entry.phone) }) {
                    Icon(Icons.Outlined.Phone, contentDescription = "Ligar para ${booking.residentName}", tint = scheme.tertiary)
                }
            } else {
                Spacer(modifier = Modifier.height(48.dp))
            }
        }
    }
}
