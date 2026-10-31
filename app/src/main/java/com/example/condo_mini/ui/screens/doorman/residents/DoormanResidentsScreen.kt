package com.example.condo_mini.ui.screens.doorman.residents

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.condo_mini.data.AppContainer
import com.example.condo_mini.domain.model.CondoUnit
import com.example.condo_mini.domain.model.User
import com.example.condo_mini.domain.model.UserRole
import com.example.condo_mini.domain.model.floorLabel
import com.example.condo_mini.domain.repository.DirectoryRepository
import com.example.condo_mini.domain.repository.SessionRepository
import com.example.condo_mini.ui.components.CondoCard
import com.example.condo_mini.ui.components.EmptyState
import com.example.condo_mini.ui.components.Eyebrow
import com.example.condo_mini.ui.components.InitialsAvatar
import com.example.condo_mini.ui.components.LoadingState
import com.example.condo_mini.ui.components.PillChip
import com.example.condo_mini.ui.components.condoTextFieldColors
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.text.Normalizer

/** Apartamento ocupado, com quem mora nele. */
data class OccupiedUnit(val unit: CondoUnit, val people: List<User>)

data class DoormanResidentsUiState(
    val isLoading: Boolean = true,
    val towers: List<String> = emptyList(),
    val tower: String? = null,
    val query: String = "",
    val units: List<OccupiedUnit> = emptyList()
)

@OptIn(ExperimentalCoroutinesApi::class)
class DoormanResidentsViewModel(
    sessionRepository: SessionRepository = AppContainer.sessionRepository,
    directoryRepository: DirectoryRepository = AppContainer.directoryRepository
) : ViewModel() {

    private val tower = MutableStateFlow<String?>(null)
    private val query = MutableStateFlow("")

    val uiState: StateFlow<DoormanResidentsUiState> = sessionRepository.currentUser
        .flatMapLatest { user ->
            val condo = user?.condoId.orEmpty()
            combine(
                directoryRepository.observeUnitsOf(condo),
                directoryRepository.observeUsersOf(condo),
                tower,
                query
            ) { units, users, tower, query ->
                val people = users
                    .filter { it.role == UserRole.RESIDENT || it.role == UserRole.MANAGER }
                    .flatMap { person -> person.units.map { it to person } }
                    .groupBy({ it.first }, { it.second })
                val needle = query.normalized()
                DoormanResidentsUiState(
                    isLoading = false,
                    towers = units.map { it.tower }.distinct(),
                    tower = tower,
                    query = query,
                    units = units
                        .filter { tower == null || it.tower == tower }
                        .mapNotNull { unit -> people[unit.label]?.let { OccupiedUnit(unit, it) } }
                        // Busca por nome, número do apartamento ou telefone.
                        .filter { occupied ->
                            needle.isBlank() || occupied.unit.number.contains(needle) ||
                                occupied.people.any { "${it.name} ${it.phone}".normalized().contains(needle) }
                        }
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DoormanResidentsUiState())

    fun onTowerSelected(value: String?) {
        tower.value = value
    }

    fun onQueryChanged(value: String) {
        query.value = value.take(40)
    }

    private fun String.normalized(): String =
        Normalizer.normalize(trim().lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")
}

@Composable
fun DoormanResidentsRoute(
    modifier: Modifier = Modifier,
    viewModel: DoormanResidentsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    DoormanResidentsScreen(
        uiState = uiState,
        onTowerSelected = viewModel::onTowerSelected,
        onQueryChanged = viewModel::onQueryChanged,
        // Abre o discador com o número: a portaria confirma a ligação, sem permissão extra.
        onCall = { phone -> context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${phone.filter { it.isDigit() }}"))) },
        modifier = modifier
    )
}

@Composable
fun DoormanResidentsScreen(
    uiState: DoormanResidentsUiState,
    onTowerSelected: (String?) -> Unit,
    onQueryChanged: (String) -> Unit,
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
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            OutlinedTextField(
                value = uiState.query,
                onValueChange = onQueryChanged,
                placeholder = { Text("Nome, apartamento ou telefone") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                colors = condoTextFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (uiState.towers.size > 1) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        PillChip(selected = uiState.tower == null, onClick = { onTowerSelected(null) }) {
                            Text("Todos", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    items(uiState.towers) { tower ->
                        PillChip(selected = uiState.tower == tower, onClick = { onTowerSelected(tower) }) {
                            Text(tower, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
        if (uiState.units.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Default.Groups,
                    title = "Ninguém encontrado",
                    message = "Confira o nome ou o número do apartamento.",
                    modifier = Modifier.height(300.dp)
                )
            }
        }
        items(uiState.units, key = { it.unit.id }) { occupied ->
            UnitCard(occupied, onCall)
        }
    }
}

@Composable
private fun UnitCard(occupied: OccupiedUnit, onCall: (String) -> Unit) {
    CondoCard(contentPadding = PaddingValues(start = 18.dp, end = 8.dp, top = 14.dp, bottom = 8.dp)) {
        Eyebrow("${occupied.unit.tower} · ${floorLabel(occupied.unit.floor)}")
        Spacer(modifier = Modifier.height(2.dp))
        Text("Apto ${occupied.unit.number}", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(4.dp))
        occupied.people.forEachIndexed { index, person ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(end = 10.dp))
            Row(
                modifier = Modifier.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                InitialsAvatar(person.name, size = 36.dp)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (person.role == UserRole.MANAGER) "${person.name} · Síndico" else person.name,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        person.phone.ifBlank { "Sem telefone" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (person.phone.isNotBlank()) {
                    IconButton(onClick = { onCall(person.phone) }) {
                        Icon(Icons.Outlined.Phone, contentDescription = "Ligar para ${person.name}", tint = MaterialTheme.colorScheme.tertiary)
                    }
                }
            }
        }
    }
}
