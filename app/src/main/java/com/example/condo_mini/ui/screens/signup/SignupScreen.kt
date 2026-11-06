package com.example.condo_mini.ui.screens.signup

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Apartment
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.condo_mini.domain.model.Condominium
import com.example.condo_mini.domain.model.Tower
import com.example.condo_mini.ui.components.CondoCard
import com.example.condo_mini.ui.components.EmptyState
import com.example.condo_mini.ui.components.IconBadge
import com.example.condo_mini.ui.components.LoadingState
import com.example.condo_mini.ui.components.PrimaryButton
import com.example.condo_mini.ui.components.UnitPicker
import com.example.condo_mini.ui.components.condoTextFieldColors
import com.example.condo_mini.ui.screens.login.LightStatusBarIcons
import com.example.condo_mini.ui.theme.CondoTheme
import com.example.condo_mini.ui.theme.CondominiTheme

@Composable
fun SignupRoute(
    onFinished: () -> Unit,
    viewModel: SignupViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    BackHandler { if (!viewModel.onBack()) onFinished() }
    SignupScreen(
        uiState = uiState,
        onBack = { if (!viewModel.onBack()) onFinished() },
        onRetry = viewModel::loadCondos,
        onQueryChanged = viewModel::onQueryChanged,
        onCondoSelected = viewModel::onCondoSelected,
        onUnitSelected = viewModel::onUnitSelected,
        onUnitConfirmed = viewModel::onUnitConfirmed,
        onNameChanged = viewModel::onNameChanged,
        onEmailChanged = viewModel::onEmailChanged,
        onPhoneChanged = viewModel::onPhoneChanged,
        onLoginChanged = viewModel::onLoginChanged,
        onPasswordChanged = viewModel::onPasswordChanged,
        onConfirmPasswordChanged = viewModel::onConfirmPasswordChanged,
        onTogglePassword = viewModel::onTogglePassword,
        onSubmit = viewModel::onSubmit,
        onFinished = onFinished
    )
}

@Composable
fun SignupScreen(
    uiState: SignupUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onQueryChanged: (String) -> Unit,
    onCondoSelected: (Condominium) -> Unit,
    onUnitSelected: (String) -> Unit,
    onUnitConfirmed: () -> Unit,
    onNameChanged: (String) -> Unit,
    onEmailChanged: (String) -> Unit,
    onPhoneChanged: (String) -> Unit,
    onLoginChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onConfirmPasswordChanged: (String) -> Unit,
    onTogglePassword: () -> Unit,
    onSubmit: () -> Unit,
    onFinished: () -> Unit
) {
    LightStatusBarIcons()
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
        ) {
            StepHeader(uiState.step, onBack)
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                when (uiState.step) {
                    SignupStep.CONDO -> CondoStep(uiState, onRetry, onQueryChanged, onCondoSelected)
                    SignupStep.UNIT -> UnitStep(uiState, onUnitSelected, onUnitConfirmed)
                    SignupStep.DETAILS -> DetailsStep(
                        uiState, onNameChanged, onEmailChanged, onPhoneChanged, onLoginChanged,
                        onPasswordChanged, onConfirmPasswordChanged, onTogglePassword, onSubmit
                    )
                    SignupStep.DONE -> DoneStep(uiState, onFinished)
                }
            }
        }
    }
}

/** Topo escuro, como o do login: voltar, em que passo está e a pergunta do passo. */
@Composable
private fun StepHeader(step: SignupStep, onBack: () -> Unit) {
    val condo = CondoTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
            .background(Brush.verticalGradient(listOf(condo.heroStart, condo.heroEnd)))
            .statusBarsPadding()
            .padding(start = 8.dp, end = 24.dp, top = 8.dp, bottom = 24.dp)
    ) {
        if (step != SignupStep.DONE) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = condo.onHero)
            }
        } else {
            Spacer(modifier = Modifier.height(48.dp))
        }
        Column(modifier = Modifier.padding(start = 16.dp)) {
            Text(
                if (step == SignupStep.DONE) "CRIAR CONTA" else "CRIAR CONTA · PASSO ${step.number} DE 3",
                style = MaterialTheme.typography.labelMedium,
                color = condo.accent
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(step.title, style = MaterialTheme.typography.headlineMedium, color = condo.onHero)
            if (step != SignupStep.DONE) {
                Spacer(modifier = Modifier.height(16.dp))
                LinearProgressIndicator(
                    progress = { step.number / 3f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(CircleShape),
                    color = condo.accent,
                    trackColor = condo.onHero.copy(alpha = 0.15f),
                    drawStopIndicator = {}
                )
            }
        }
    }
}

@Composable
private fun CondoStep(
    uiState: SignupUiState,
    onRetry: () -> Unit,
    onQueryChanged: (String) -> Unit,
    onCondoSelected: (Condominium) -> Unit
) {
    when {
        uiState.isLoading -> LoadingState(Modifier.height(240.dp))
        uiState.loadError != null -> {
            EmptyState(
                icon = Icons.Outlined.Apartment,
                title = "Não deu para carregar os prédios",
                message = uiState.loadError,
                modifier = Modifier.height(240.dp)
            )
            PrimaryButton(text = "Tentar de novo", onClick = onRetry)
        }
        uiState.condos.isEmpty() -> EmptyState(
            icon = Icons.Outlined.Apartment,
            title = "Nenhum prédio disponível",
            message = "Fale com a administração do seu condomínio.",
            modifier = Modifier.height(240.dp)
        )
        else -> {
            Text(
                "Escolha o condomínio onde você mora. A administradora confere o pedido antes de liberar o acesso.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (uiState.condos.size > 4) {
                OutlinedTextField(
                    value = uiState.query,
                    onValueChange = onQueryChanged,
                    placeholder = { Text("Buscar por nome ou endereço") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    colors = condoTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            uiState.visibleCondos.forEach { condo ->
                CondoCard(onClick = { onCondoSelected(condo) }, contentPadding = PaddingValues(0.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconBadge(icon = Icons.Outlined.Apartment, size = 44.dp)
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(condo.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (condo.address.isNotBlank()) {
                                Text(
                                    condo.address,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Escolher ${condo.name}",
                            tint = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
            if (uiState.visibleCondos.isEmpty()) {
                Text(
                    "Nenhum prédio com esse nome. Confira a grafia ou busque pela rua.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun UnitStep(
    uiState: SignupUiState,
    onUnitSelected: (String) -> Unit,
    onUnitConfirmed: () -> Unit
) {
    val condo = uiState.condo ?: return
    Text(
        condo.name,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    UnitPicker(
        units = condo.units,
        selected = setOfNotNull(uiState.unit),
        onUnitClick = { onUnitSelected(it.label) }
    )
    PrimaryButton(
        text = "Continuar",
        onClick = onUnitConfirmed,
        enabled = uiState.unit != null,
        trailingIcon = Icons.AutoMirrored.Filled.ArrowForward
    )
}

@Composable
private fun DetailsStep(
    uiState: SignupUiState,
    onNameChanged: (String) -> Unit,
    onEmailChanged: (String) -> Unit,
    onPhoneChanged: (String) -> Unit,
    onLoginChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onConfirmPasswordChanged: (String) -> Unit,
    onTogglePassword: () -> Unit,
    onSubmit: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    val next = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })

    ChosenSummary(uiState)
    SignupField(uiState.name, onNameChanged, "Nome completo", Icons.Outlined.Person, next,
        KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next))
    SignupField(uiState.email, onEmailChanged, "E-mail (opcional)", Icons.Outlined.Email, next,
        KeyboardOptions(keyboardType = KeyboardType.Email, autoCorrectEnabled = false, imeAction = ImeAction.Next))
    SignupField(uiState.phone, onPhoneChanged, "Telefone (opcional)", Icons.Outlined.Phone, next,
        KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next))

    Text("Acesso ao app", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 6.dp))
    SignupField(uiState.login, onLoginChanged, "Usuário", Icons.Outlined.Badge, next,
        KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
        supporting = "Sem espaços. É o que você vai digitar para entrar.")
    val visual = if (uiState.showPassword) VisualTransformation.None else PasswordVisualTransformation()
    val toggle: @Composable () -> Unit = {
        IconButton(onClick = onTogglePassword) {
            Icon(
                if (uiState.showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                contentDescription = if (uiState.showPassword) "Esconder senha" else "Mostrar senha"
            )
        }
    }
    SignupField(uiState.password, onPasswordChanged, "Senha", Icons.Outlined.Lock, next,
        KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
        supporting = "Pelo menos 6 caracteres.", visual = visual, trailing = toggle)
    SignupField(uiState.confirmPassword, onConfirmPasswordChanged, "Confirme a senha", Icons.Outlined.Lock,
        KeyboardActions(onDone = { focusManager.clearFocus(); onSubmit() }),
        KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        visual = visual)

    if (uiState.error != null) {
        Text(uiState.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
    }
    PrimaryButton(
        text = "Enviar pedido",
        onClick = {
            focusManager.clearFocus()
            onSubmit()
        },
        loading = uiState.isSubmitting,
        trailingIcon = Icons.AutoMirrored.Filled.ArrowForward
    )
    Text(
        "A administradora confere se você mora mesmo no apartamento. Depois disso, é só entrar com o usuário e a senha que você criou.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun SignupField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    actions: KeyboardActions,
    options: KeyboardOptions,
    supporting: String? = null,
    visual: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        trailingIcon = trailing,
        supportingText = supporting?.let { { Text(it) } },
        visualTransformation = visual,
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        colors = condoTextFieldColors(),
        keyboardOptions = options,
        keyboardActions = actions,
        modifier = Modifier.fillMaxWidth()
    )
}

/** Prédio e apartamento escolhidos, para conferir antes de enviar. */
@Composable
private fun ChosenSummary(uiState: SignupUiState) {
    CondoCard(contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon = Icons.Outlined.Apartment, size = 40.dp)
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(uiState.unit.orEmpty(), style = MaterialTheme.typography.titleMedium)
                Text(uiState.condo?.name.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DoneStep(uiState: SignupUiState, onFinished: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.tertiaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.HourglassTop, contentDescription = null, tint = CondoTheme.colors.accent, modifier = Modifier.size(32.dp))
        }
        Spacer(modifier = Modifier.height(18.dp))
        Text("Agora é com a administradora", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Ela vai conferir seu pedido. Quando aprovar, entre com o usuário e a senha que você criou.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    ChosenSummary(uiState)
    CondoCard(contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon = Icons.Outlined.Badge, size = 40.dp)
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text("Seu usuário", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(uiState.login, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
    PrimaryButton(text = "Voltar para o login", onClick = onFinished)
}

private val previewCondo = Condominium(
    id = "c1",
    name = "Residencial Jardins",
    address = "Rua Oscar Freire, 1200 · Jardins, São Paulo",
    towers = listOf(Tower("t1", "Bloco A", 6, 4), Tower("t2", "Bloco B", 6, 4))
)

@Composable
private fun SignupPreview(state: SignupUiState) {
    CondominiTheme {
        SignupScreen(
            uiState = state, onBack = {}, onRetry = {}, onQueryChanged = {}, onCondoSelected = {}, onUnitSelected = {},
            onUnitConfirmed = {}, onNameChanged = {}, onEmailChanged = {}, onPhoneChanged = {}, onLoginChanged = {},
            onPasswordChanged = {}, onConfirmPasswordChanged = {}, onTogglePassword = {}, onSubmit = {}, onFinished = {}
        )
    }
}

@Preview(name = "Prédio", showBackground = true)
@Preview(name = "Prédio · compacto + fonte 150%", showBackground = true, widthDp = 320, heightDp = 640, fontScale = 1.5f)
@Composable
private fun SignupCondoPreview() = SignupPreview(
    SignupUiState(isLoading = false, condos = listOf(previewCondo, previewCondo.copy(id = "c2", name = "Edifício Vista Mar", address = "Av. Atlântica, 3400")))
)

@Preview(name = "Dados", showBackground = true)
@Preview(name = "Dados · compacto + fonte 150%", showBackground = true, widthDp = 320, heightDp = 640, fontScale = 1.5f)
@Composable
private fun SignupDetailsPreview() = SignupPreview(
    SignupUiState(isLoading = false, step = SignupStep.DETAILS, condo = previewCondo, unit = "Apto 302 - Bloco A", name = "Marina Costa")
)

@Preview(name = "Enviado", showBackground = true)
@Composable
private fun SignupDonePreview() = SignupPreview(
    SignupUiState(isLoading = false, step = SignupStep.DONE, condo = previewCondo, unit = "Apto 302 - Bloco A", login = "marina.costa")
)
