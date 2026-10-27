package com.example.condo_mini.ui.screens.login

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.Apartment
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.condo_mini.domain.model.UserRole
import com.example.condo_mini.ui.components.Eyebrow
import com.example.condo_mini.ui.components.PillChip
import com.example.condo_mini.ui.components.PrimaryButton
import com.example.condo_mini.ui.components.SectionHeader
import com.example.condo_mini.ui.components.condoTextFieldColors
import com.example.condo_mini.ui.theme.CondominiTheme

@Composable
fun LoginScreen(
    uiState: LoginUiState,
    onUserNameChanged: (String) -> Unit,
    onUnitNumberChanged: (String) -> Unit,
    onRoleChanged: (UserRole) -> Unit,
    onLoginClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))
            Monogram()
            Spacer(modifier = Modifier.height(28.dp))
            Eyebrow("Residencial")
            Spacer(modifier = Modifier.height(8.dp))
            Text("Condo-mini", style = MaterialTheme.typography.displaySmall)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Seu condomínio, com a atenção\nde um concierge.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(40.dp))

            SectionHeader("Acessar como")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(UserRole.RESIDENT to "Morador", UserRole.MANAGER to "Síndico").forEach { (role, label) ->
                    PillChip(
                        selected = uiState.role == role,
                        onClick = { onRoleChanged(role) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(label, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            OutlinedTextField(
                value = uiState.userName,
                onValueChange = onUserNameChanged,
                label = { Text(if (uiState.role == UserRole.RESIDENT) "Nome do morador" else "Seu nome") },
                leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                colors = condoTextFieldColors(),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = uiState.unitNumber,
                onValueChange = onUnitNumberChanged,
                label = { Text("Apartamento e bloco") },
                placeholder = { Text("Ex.: Apto 102 - Bloco B") },
                leadingIcon = { Icon(Icons.Outlined.Apartment, contentDescription = null) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                colors = condoTextFieldColors(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onLoginClick() }),
                modifier = Modifier.fillMaxWidth()
            )

            if (uiState.errorMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = uiState.errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            PrimaryButton(
                text = "Entrar",
                onClick = onLoginClick,
                trailingIcon = Icons.AutoMirrored.Filled.ArrowForward
            )

            Spacer(modifier = Modifier.height(32.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Acesso simplificado nesta versão. A senha ainda não é exigida.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Monograma da marca: inicial em serifa dentro de anel champanhe. */
@Composable
private fun Monogram() {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(84.dp)
            .border(1.dp, scheme.tertiary, CircleShape)
            .padding(6.dp)
            .clip(CircleShape)
            .background(scheme.primary),
        contentAlignment = Alignment.Center
    ) {
        Text("C", style = MaterialTheme.typography.displaySmall, color = scheme.tertiaryContainer)
    }
}

@Preview(showBackground = true)
@Composable
fun LoginScreenPreview() {
    CondominiTheme {
        LoginScreen(
            uiState = LoginUiState(userName = "Carlos Eduardo", unitNumber = "Ap 304"),
            onUserNameChanged = {},
            onUnitNumberChanged = {},
            onRoleChanged = {},
            onLoginClick = {}
        )
    }
}
