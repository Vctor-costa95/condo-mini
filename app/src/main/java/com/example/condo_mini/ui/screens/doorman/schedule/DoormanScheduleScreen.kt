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
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Handyman
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.example.condo_mini.domain.model.ReservationStatus
import com.example.condo_mini.domain.model.ServiceBooking
import com.example.condo_mini.domain.model.ServiceType
import com.example.condo_mini.domain.repository.DirectoryRepository
import com.example.condo_mini.domain.repository.ServiceBookingRepository
import com.example.condo_mini.domain.repository.SessionRepository
import com.example.condo_mini.ui.components.CondoCard
import com.example.condo_mini.ui.components.EmptyState
import com.example.condo_mini.ui.components.Eyebrow
import com.example.condo_mini.ui.components.HeroCard
import com.example.condo_mini.ui.components.LoadingState
import com.example.condo_mini.ui.components.SectionHeader
import com.example.condo_mini.ui.theme.CondoTheme
import com.example.condo_mini.ui.util.formatWeekday
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

data class ScheduleDay(val date: LocalDate, val entries: List<ScheduleEntry>)

data class DoormanScheduleUiState(
    val isLoading: Boolean = true,
    val today: List<ScheduleEntry> = emptyList(),
    val upcoming: List<ScheduleDay> = emptyList()
)

@OptIn(ExperimentalCoroutinesApi::class)
class DoormanScheduleViewModel(
    sessionRepository: SessionRepository = AppContainer.sessionRepository,
    serviceBookingRepository: ServiceBookingRepository = AppContainer.serviceBookingRepository,
    directoryRepository: DirectoryRepository = AppContainer.directoryRepository
) : ViewModel() {

    val uiState: StateFlow<DoormanScheduleUiState> = sessionRepository.currentUser
        .flatMapLatest { user ->
            val condo = user?.condoId.orEmpty()
            combine(
                serviceBookingRepository.observeAllBookings(condo),
                directoryRepository.observeUsersOf(condo)
            ) { bookings, users ->
                val today = LocalDate.now()
                val last = today.plusDays(SCHEDULE_DAYS)
                // A portaria só vê o que o síndico aprovou.
                val entries = bookings
                    .filter { it.status == ReservationStatus.CONFIRMED && !it.date.isBefore(today) && !it.date.isAfter(last) }
                    .map { booking ->
                        val phone = users.firstOrNull { it.name == booking.residentName && booking.unit in it.units }?.phone
                        ScheduleEntry(booking, phone.orEmpty())
                    }
                DoormanScheduleUiState(
                    isLoading = false,
                    today = entries.filter { it.booking.date == today },
                    upcoming = entries.filter { it.booking.date.isAfter(today) }
                        .groupBy { it.booking.date }
                        .map { (date, list) -> ScheduleDay(date, list) }
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
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { TodaySummary(uiState) }
        if (uiState.today.isNotEmpty()) {
            item { SectionHeader("Hoje", modifier = Modifier.padding(horizontal = 4.dp)) }
            items(uiState.today, key = { it.booking.id }) { ScheduleCard(it, onCall) }
        }
        uiState.upcoming.forEach { day ->
            item(key = day.date.toString()) {
                SectionHeader(
                    if (day.date == LocalDate.now().plusDays(1)) "Amanhã" else day.date.formatWeekday(),
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
            items(day.entries, key = { it.booking.id }) { ScheduleCard(it, onCall) }
        }
        if (uiState.today.isEmpty() && uiState.upcoming.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Outlined.EventAvailable,
                    title = "Agenda livre",
                    message = "Nenhuma mudança ou reparo aprovado para as próximas duas semanas.",
                    modifier = Modifier.height(280.dp)
                )
            }
        }
    }
}

@Composable
private fun TodaySummary(uiState: DoormanScheduleUiState) {
    val condo = CondoTheme.colors
    val moves = uiState.today.count { it.booking.type == ServiceType.MOVE }
    val repairs = uiState.today.size - moves
    HeroCard {
        Eyebrow("Hoje na portaria", color = condo.accent)
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${uiState.today.size}", style = MaterialTheme.typography.displaySmall, color = condo.onHero)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                if (uiState.today.size == 1) "agendamento" else "agendamentos",
                style = MaterialTheme.typography.bodyMedium,
                color = condo.onHeroMuted,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            if (uiState.today.isEmpty()) {
                uiState.upcoming.firstOrNull()?.let { "Próximo: ${it.date.formatWeekday()}" } ?: "Nada marcado"
            } else {
                listOfNotNull(
                    moves.takeIf { it > 0 }?.let { if (it == 1) "1 mudança" else "$it mudanças" },
                    repairs.takeIf { it > 0 }?.let { if (it == 1) "1 reparo" else "$it reparos" }
                ).joinToString(" · ")
            },
            style = MaterialTheme.typography.bodySmall,
            color = condo.onHeroMuted
        )
    }
}

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
