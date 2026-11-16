package com.example.condo_mini.ui.screens.admin.users

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apartment
import androidx.compose.material.icons.outlined.HowToReg
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.condo_mini.domain.model.CondoUnit
import com.example.condo_mini.domain.model.SignupRequest
import com.example.condo_mini.domain.model.User
import com.example.condo_mini.ui.components.CondoCard
import com.example.condo_mini.ui.components.Eyebrow
import com.example.condo_mini.ui.components.InitialsAvatar
import com.example.condo_mini.ui.components.PrimaryButton
import com.example.condo_mini.ui.components.SectionHeader
import com.example.condo_mini.ui.components.StatusChip
import com.example.condo_mini.ui.components.StatusTone
import com.example.condo_mini.ui.components.UnitPicker
import com.example.condo_mini.ui.components.condoTextFieldColors
import com.example.condo_mini.ui.components.firstNames
import com.example.condo_mini.ui.util.relative

/** Cabeçalho da seção, acima dos cartões: quantos esperam e o que fazer. */
@Composable
fun SignupRequestsHeader(count: Int, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        SectionHeader("Pedidos de cadastro", trailing = if (count == 1) "1 aguardando" else "$count aguardando")
        Text(
            "Pessoas que criaram a conta pelo app. Confira o apartamento antes de aceitar: elas entram com o usuário e a senha que escolheram.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Pedido feito pelo app: quem é, o apartamento pedido e os dois caminhos. */
@Composable
fun SignupRequestCard(
    request: SignupRequest,
    onReview: () -> Unit,
    onReject: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    CondoCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            InitialsAvatar(request.name, size = 44.dp)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(request.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "Pediu ${request.createdAt.relative()} · ${request.login}",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            StatusChip("Cadastro", StatusTone.WARNING)
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(scheme.tertiaryContainer)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Icon(Icons.Outlined.Apartment, contentDescription = null, tint = scheme.onTertiaryContainer, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Text(request.unit, style = MaterialTheme.typography.titleSmall, color = scheme.onTertiaryContainer)
        }
        val contact = listOf(request.email, request.phone).filter { it.isNotBlank() }
        if (contact.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                contact.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = onReject,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.weight(1f).height(48.dp)
            ) {
                Text("Recusar", color = scheme.error, style = MaterialTheme.typography.labelLarge)
            }
            androidx.compose.material3.Button(
                onClick = onReview,
                shape = MaterialTheme.shapes.medium,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = scheme.primary,
                    contentColor = scheme.onPrimary
                ),
                modifier = Modifier.weight(1f).height(48.dp)
            ) {
                Text("Aceitar", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** Antes de aceitar: os dados da pessoa e o apartamento, já marcado com o que ela pediu. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignupReviewSheet(
    review: SignupReview,
    units: List<CondoUnit>,
    occupantsByUnit: Map<String, List<User>>,
    onUnitSelected: (String) -> Unit,
    onApprove: () -> Unit,
    onDismiss: () -> Unit
) {
    val request = review.request
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            Eyebrow("Pedido de cadastro")
            Spacer(modifier = Modifier.height(6.dp))
            Text(request.name, style = MaterialTheme.typography.headlineLarge)
            Text(
                "Pediu ${request.createdAt.relative()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            InfoRow("Usuário", request.login)
            InfoRow("E-mail", request.email.ifBlank { "—" })
            InfoRow("Telefone", request.phone.ifBlank { "—" })

            Spacer(modifier = Modifier.height(16.dp))
            SectionHeader("Apartamento", trailing = review.unit)
            Text(
                if (review.unit == request.unit) {
                    "É o que a pessoa escolheu. Se estiver errado, toque no certo."
                } else {
                    "Trocado: a pessoa tinha escolhido ${request.unit}."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            UnitPicker(
                units = units,
                selected = setOf(review.unit),
                onUnitClick = { onUnitSelected(it.label) },
                initialUnit = review.unit,
                unitCaption = { unit -> occupantsByUnit[unit.label].firstNames() ?: "Vago" },
                showSummary = false
            )

            review.error?.let {
                Spacer(modifier = Modifier.height(12.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(modifier = Modifier.height(20.dp))
            PrimaryButton(
                text = "Aceitar no ${review.unit.substringBefore(" - ")}",
                onClick = onApprove,
                loading = review.isSaving,
                trailingIcon = Icons.Outlined.HowToReg
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Vira morador com o usuário e a senha que a pessoa escolheu.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(88.dp)
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Recusa com motivo obrigatório: é o que a pessoa lê quando tenta entrar. */
@Composable
fun SignupRejectionDialog(
    rejection: SignupRejection,
    onReasonChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background,
        title = { Text("Recusar pedido?", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(modifier = Modifier.imePadding()) {
                Text("${rejection.request.name} · ${rejection.request.unit}. A pessoa vê o motivo quando tentar entrar.")
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = rejection.reason,
                    onValueChange = onReasonChange,
                    label = { Text("Motivo") },
                    placeholder = { Text("Ex.: não mora neste apartamento") },
                    shape = MaterialTheme.shapes.medium,
                    colors = condoTextFieldColors(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    isError = rejection.error != null,
                    supportingText = rejection.error?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !rejection.isSaving) {
                Text("Recusar", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Voltar", color = MaterialTheme.colorScheme.onSurface) }
        }
    )
}
