package com.example.condo_mini.ui.screens.login

import android.app.Activity
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.example.condo_mini.ui.components.PrimaryButton
import com.example.condo_mini.ui.components.condoTextFieldColors
import com.example.condo_mini.ui.theme.CondoTheme
import com.example.condo_mini.ui.theme.CondominiTheme

@Composable
fun LoginScreen(
    uiState: LoginUiState,
    onLoginChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onTogglePassword: () -> Unit,
    onLoginClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    LightStatusBarIcons()
    val focusManager = LocalFocusManager.current

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
        ) {
            BrandHeader()

            // O cartão sobe um pouco sobre o topo escuro, para as duas partes se encaixarem.
            Surface(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .offset(y = (-36).dp)
                    .fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                shadowElevation = 6.dp
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("Entrar", style = MaterialTheme.typography.headlineSmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Use o usuário e a senha que a administração do condomínio passou para você.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(20.dp))

                    OutlinedTextField(
                        value = uiState.login,
                        onValueChange = onLoginChanged,
                        label = { Text("Usuário") },
                        leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null) },
                        singleLine = true,
                        isError = uiState.errorMessage != null,
                        shape = MaterialTheme.shapes.medium,
                        colors = condoTextFieldColors(),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            autoCorrectEnabled = false,
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = uiState.password,
                        onValueChange = onPasswordChanged,
                        label = { Text("Senha") },
                        leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = onTogglePassword) {
                                Icon(
                                    if (uiState.showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    contentDescription = if (uiState.showPassword) "Esconder senha" else "Mostrar senha"
                                )
                            }
                        },
                        visualTransformation = if (uiState.showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        isError = uiState.errorMessage != null,
                        shape = MaterialTheme.shapes.medium,
                        colors = condoTextFieldColors(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            focusManager.clearFocus()
                            onLoginClick()
                        }),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (uiState.errorMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = uiState.errorMessage,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    PrimaryButton(
                        text = "Entrar",
                        onClick = {
                            focusManager.clearFocus()
                            onLoginClick()
                        },
                        trailingIcon = Icons.AutoMirrored.Filled.ArrowForward
                    )
                }
            }

            Row(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .offset(y = (-16).dp)
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.HelpOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Esqueceu a senha ou ainda não tem acesso? Fale com a administração do seu condomínio.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Topo escuro com a marca e, em poucas palavras, para que o app serve. */
@Composable
private fun BrandHeader() {
    val condo = CondoTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 36.dp, bottomEnd = 36.dp))
            .background(Brush.verticalGradient(listOf(condo.heroStart, condo.heroEnd)))
            .statusBarsPadding()
            .padding(start = 28.dp, end = 28.dp, top = 40.dp, bottom = 68.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Monogram()
        Spacer(modifier = Modifier.height(20.dp))
        Text("Condo-mini", style = MaterialTheme.typography.displaySmall, color = condo.onHero)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Avisos, encomendas, reservas e boletos\ndo seu prédio, tudo num lugar só.",
            style = MaterialTheme.typography.bodyMedium,
            color = condo.onHeroMuted,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FeatureTag(Icons.Outlined.Inventory2, "Encomendas")
            FeatureTag(Icons.Outlined.Campaign, "Avisos")
            FeatureTag(Icons.Outlined.EventAvailable, "Reservas")
        }
    }
}

@Composable
private fun FeatureTag(icon: ImageVector, label: String) {
    val condo = CondoTheme.colors
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .border(1.dp, condo.accent.copy(alpha = 0.35f), CircleShape)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = condo.accent, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = condo.onHero)
    }
}

/** Monograma da marca: inicial em serifa dentro de anel champanhe. */
@Composable
private fun Monogram() {
    val condo = CondoTheme.colors
    Box(
        modifier = Modifier
            .size(80.dp)
            .border(1.dp, condo.accent, CircleShape)
            .padding(6.dp)
            .clip(CircleShape)
            .background(condo.onHero.copy(alpha = 0.08f)),
        contentAlignment = Alignment.Center
    ) {
        Text("C", style = MaterialTheme.typography.displaySmall, color = condo.accent)
    }
}

/** O topo é escuro nos dois temas: os ícones da barra de status ficam claros enquanto a tela está aberta. */
@Composable
private fun LightStatusBarIcons() {
    if (LocalInspectionMode.current) return
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = false
        onDispose { previous?.let { controller.isAppearanceLightStatusBars = it } }
    }
}

@Preview(showBackground = true)
@Composable
fun LoginScreenPreview() {
    CondominiTheme {
        LoginScreen(
            uiState = LoginUiState(login = "morador"),
            onLoginChanged = {},
            onPasswordChanged = {},
            onTogglePassword = {},
            onLoginClick = {}
        )
    }
}
