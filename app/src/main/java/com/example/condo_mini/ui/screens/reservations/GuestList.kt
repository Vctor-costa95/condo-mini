package com.example.condo_mini.ui.screens.reservations

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.example.condo_mini.domain.model.MAX_GUEST_NAME
import com.example.condo_mini.domain.model.cleanGuestName
import com.example.condo_mini.domain.model.guestLimit
import com.example.condo_mini.domain.model.hasGuest
import com.example.condo_mini.ui.components.Eyebrow
import com.example.condo_mini.ui.components.InitialsAvatar
import com.example.condo_mini.ui.components.PrimaryButton
import com.example.condo_mini.ui.components.SectionHeader
import com.example.condo_mini.ui.components.condoTextFieldColors
import com.example.condo_mini.ui.util.formatDate

/** "Nenhum convidado", "1 convidado", "5 convidados". */
fun guestCountLabel(count: Int): String = when (count) {
    0 -> "Nenhum convidado"
    1 -> "1 convidado"
    else -> "$count convidados"
}

/**
 * Lista de convidados que o morador monta: um campo para digitar o nome (o "Enter" do teclado
 * já adiciona, para emendar um nome no outro) e os nomes abaixo, cada um com o X para tirar.
 */
@Composable
fun GuestListEditor(
    guests: List<String>,
    limit: Int,
    onChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    var draft by rememberSaveable { mutableStateOf("") }
    var hint by rememberSaveable { mutableStateOf<String?>(null) }
    val isFull = guests.size >= limit

    fun add() {
        val name = draft.cleanGuestName()
        hint = when {
            name.isEmpty() -> null
            guests.hasGuest(name) -> "$name já está na lista."
            isFull -> "O espaço comporta até $limit convidados."
            else -> {
                onChange(guests + name)
                draft = ""
                null
            }
        }
    }

    Column(modifier = modifier) {
        SectionHeader("Convidados", trailing = if (guests.isEmpty()) "Opcional" else "${guests.size} de $limit")
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            "A portaria libera a entrada de quem estiver na lista.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = draft,
            onValueChange = {
                draft = it.take(MAX_GUEST_NAME)
                hint = null
            },
            label = { Text(if (isFull) "Lista completa" else "Nome do convidado") },
            enabled = !isFull,
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            colors = condoTextFieldColors(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { add() }),
            trailingIcon = {
                IconButton(onClick = ::add, enabled = draft.isNotBlank() && !isFull) {
                    Icon(
                        Icons.Outlined.PersonAdd,
                        contentDescription = "Adicionar à lista",
                        tint = if (draft.isNotBlank()) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        hint?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 6.dp, start = 4.dp)
            )
        }
        if (guests.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                guests.forEachIndexed { index, name ->
                    if (index > 0) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(start = 62.dp))
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 14.dp, top = 4.dp, bottom = 4.dp)
                    ) {
                        InitialsAvatar(name, size = 34.dp)
                        Spacer(modifier = Modifier.width(14.dp))
                        Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        IconButton(onClick = { onChange(guests - name) }) {
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = "Tirar $name da lista",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Ficha para mexer nos convidados de uma reserva já feita. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuestsSheet(
    state: GuestsEditState,
    onChange: (List<String>) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    val reservation = state.reservation
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .imePadding()
                .padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.Top
        ) {
            Eyebrow("Lista da portaria")
            Spacer(modifier = Modifier.height(6.dp))
            Text(reservation.area.name, style = MaterialTheme.typography.headlineLarge)
            Text(
                "${reservation.date.formatDate()} · ${reservation.timeLabel}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))
            GuestListEditor(guests = state.guests, limit = reservation.area.guestLimit, onChange = onChange)
            state.error?.let {
                Spacer(modifier = Modifier.height(12.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(modifier = Modifier.height(24.dp))
            PrimaryButton(
                text = "Salvar lista",
                onClick = onSave,
                enabled = state.guests != reservation.guests,
                loading = state.isSaving
            )
        }
    }
}
