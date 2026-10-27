package com.example.condo_mini.ui.screens.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.HomeWork
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.condo_mini.domain.model.User
import com.example.condo_mini.domain.model.UserRole
import com.example.condo_mini.ui.components.CondoCard
import com.example.condo_mini.ui.components.Eyebrow
import com.example.condo_mini.ui.components.IconBadge
import com.example.condo_mini.ui.components.InitialsAvatar
import com.example.condo_mini.ui.components.LoadingState
import com.example.condo_mini.ui.components.SectionHeader
import com.example.condo_mini.ui.components.condoTextFieldColors
import com.example.condo_mini.ui.theme.CondominiTheme

@Composable
fun ProfileRoute(
    onLogoutClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = viewModel()
) {
    val user by viewModel.user.collectAsStateWithLifecycle()
    val current = user
    if (current == null) {
        LoadingState(modifier)
        return
    }
    ProfileScreen(
        user = current,
        onSaveContact = viewModel::onSaveContact,
        onLogoutClick = onLogoutClick,
        modifier = modifier
    )
}

@Composable
fun ProfileScreen(
    user: User,
    onSaveContact: (email: String, phone: String) -> Unit,
    onLogoutClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var editingContact by rememberSaveable { mutableStateOf(false) }
    var notificationsEnabled by rememberSaveable { mutableStateOf(true) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            InitialsAvatar(name = user.name, size = 96.dp)
            Spacer(modifier = Modifier.height(16.dp))
            Text(user.name, style = MaterialTheme.typography.headlineLarge)
            Spacer(modifier = Modifier.height(4.dp))
            Eyebrow("${user.role.label} · ${user.unit}")
        }

        SectionHeader("Meus dados", modifier = Modifier.padding(horizontal = 4.dp))
        CondoCard(contentPadding = PaddingValues(vertical = 4.dp)) {
            ProfileRow(Icons.Outlined.HomeWork, "Unidade", user.unit)
            RowDivider()
            ProfileRow(Icons.Outlined.Badge, "Perfil de acesso", user.role.label)
        }

        SectionHeader("Contato", modifier = Modifier.padding(horizontal = 4.dp))
        CondoCard(contentPadding = PaddingValues(vertical = 4.dp)) {
            ProfileRow(
                Icons.Outlined.Email,
                "E-mail",
                user.email.ifBlank { "Não informado" },
                onClick = { editingContact = true }
            )
            RowDivider()
            ProfileRow(
                Icons.Outlined.Phone,
                "Telefone",
                user.phone.ifBlank { "Não informado" },
                onClick = { editingContact = true }
            )
        }

        SectionHeader("Preferências", modifier = Modifier.padding(horizontal = 4.dp))
        CondoCard(contentPadding = PaddingValues(vertical = 4.dp)) {
            ProfileRow(
                Icons.Outlined.Notifications,
                "Notificações",
                "Avisos, encomendas e boletos",
                trailing = {
                    Switch(
                        checked = notificationsEnabled,
                        onCheckedChange = { notificationsEnabled = it },
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            checkedThumbColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    )
                }
            )
            RowDivider()
            ProfileRow(Icons.Outlined.Info, "Sobre", "Condo-mini · versão 1.0")
        }

        Spacer(modifier = Modifier.height(8.dp))
        TextButton(
            onClick = onLogoutClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.Logout,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Sair da conta", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
        }
        Spacer(modifier = Modifier.height(16.dp))
    }

    if (editingContact) {
        EditContactDialog(
            user = user,
            onDismiss = { editingContact = false },
            onSave = { email, phone ->
                onSaveContact(email, phone)
                editingContact = false
            }
        )
    }
}

@Composable
private fun ProfileRow(
    icon: ImageVector,
    label: String,
    value: String,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBadge(icon = icon, size = 40.dp)
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
        when {
            trailing != null -> trailing()
            onClick != null -> Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Editar",
                tint = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant,
        modifier = Modifier.padding(start = 70.dp)
    )
}

@Composable
private fun EditContactDialog(
    user: User,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var email by rememberSaveable { mutableStateOf(user.email) }
    var phone by rememberSaveable { mutableStateOf(user.phone) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background,
        title = { Text("Editar contato", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("E-mail") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    colors = condoTextFieldColors(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Telefone") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    colors = condoTextFieldColors(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(email, phone) }) {
                Text("Salvar", color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
private fun ProfileScreenPreview() {
    CondominiTheme {
        ProfileScreen(
            user = User("1", "Ana Clara", "Apto 201 - Bloco A", UserRole.RESIDENT, "ana@email.com", "(11) 98888-7777"),
            onSaveContact = { _, _ -> },
            onLogoutClick = {}
        )
    }
}
