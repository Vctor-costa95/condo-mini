package com.example.condo_mini.ui.screens.login

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.condo_mini.data.AppContainer
import com.example.condo_mini.data.remote.userMessage
import com.example.condo_mini.domain.repository.SessionRepository
import com.example.condo_mini.ui.components.Eyebrow
import com.example.condo_mini.ui.components.PrimaryButton
import com.example.condo_mini.ui.components.condoTextFieldColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ForgotPasswordUiState(
    val login: String = "",
    /** E-mail mascarado para onde o código foi; null enquanto não pediu. */
    val sentTo: String? = null,
    val code: String = "",
    val password: String = "",
    val showPassword: Boolean = false,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null
)

class ForgotPasswordViewModel(
    private val sessionRepository: SessionRepository = AppContainer.sessionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ForgotPasswordUiState())
    val uiState: StateFlow<ForgotPasswordUiState> = _uiState.asStateFlow()

    fun start(login: String) {
        _uiState.value = ForgotPasswordUiState(login = login.trim())
    }

    fun onLoginChanged(value: String) = _uiState.update { it.copy(login = value, errorMessage = null) }
    fun onCodeChanged(value: String) = _uiState.update { it.copy(code = value.filter(Char::isDigit).take(6), errorMessage = null) }
    fun onPasswordChanged(value: String) = _uiState.update { it.copy(password = value, errorMessage = null) }
    fun onTogglePassword() = _uiState.update { it.copy(showPassword = !it.showPassword) }

    /** Também serve para "Mandar outro código". */
    fun sendCode() {
        val state = _uiState.value
        if (state.isSubmitting) return
        if (state.login.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Informe seu usuário.") }
            return
        }
        _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
        viewModelScope.launch {
            sessionRepository.requestPasswordReset(state.login)
                .onSuccess { email -> _uiState.update { it.copy(isSubmitting = false, sentTo = email, code = "") } }
                .onFailure { e -> _uiState.update { it.copy(isSubmitting = false, errorMessage = e.userMessage()) } }
        }
    }

    fun confirm(onDone: (login: String) -> Unit) {
        val state = _uiState.value
        if (state.isSubmitting) return
        when {
            state.code.length != 6 -> return _uiState.update { it.copy(errorMessage = "Digite os 6 números do código.") }
            state.password.length < 6 -> return _uiState.update { it.copy(errorMessage = "A senha precisa ter pelo menos 6 caracteres.") }
        }
        _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
        viewModelScope.launch {
            sessionRepository.confirmPasswordReset(state.login, state.code, state.password)
                .onSuccess {
                    _uiState.update { it.copy(isSubmitting = false) }
                    onDone(state.login.trim())
                }
                .onFailure { e -> _uiState.update { it.copy(isSubmitting = false, errorMessage = e.userMessage()) } }
        }
    }
}

/**
 * "Esqueci a senha": primeiro o usuário (o código vai para o e-mail do cadastro),
 * depois o código e a senha nova. Sem e-mail no cadastro, a mensagem da API manda
 * falar com a administração.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForgotPasswordSheet(
    onDismiss: () -> Unit,
    onPasswordChanged: (login: String) -> Unit,
    viewModel: ForgotPasswordViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            Eyebrow("Acesso")
            Spacer(modifier = Modifier.height(6.dp))
            Text("Criar senha nova", style = MaterialTheme.typography.headlineLarge)
            Spacer(modifier = Modifier.height(8.dp))
            val sentTo = state.sentTo
            Text(
                if (sentTo == null) {
                    "Informe seu usuário: vamos mandar um código para o e-mail do seu cadastro, e com ele você escolhe a senha nova."
                } else {
                    "Enviamos um código de 6 números para $sentTo. Ele vale por 15 minutos; confira também o spam."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(20.dp))
            if (sentTo == null) {
                OutlinedTextField(
                    value = state.login,
                    onValueChange = viewModel::onLoginChanged,
                    label = { Text("Usuário") },
                    singleLine = true,
                    isError = state.errorMessage != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Send),
                    shape = MaterialTheme.shapes.medium,
                    colors = condoTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                OutlinedTextField(
                    value = state.code,
                    onValueChange = viewModel::onCodeChanged,
                    label = { Text("Código do e-mail") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Next),
                    shape = MaterialTheme.shapes.medium,
                    colors = condoTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = state.password,
                    onValueChange = viewModel::onPasswordChanged,
                    label = { Text("Senha nova") },
                    supportingText = { Text("Pelo menos 6 caracteres") },
                    singleLine = true,
                    visualTransformation = if (state.showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = viewModel::onTogglePassword) {
                            Icon(
                                if (state.showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = if (state.showPassword) "Esconder senha" else "Mostrar senha"
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    shape = MaterialTheme.shapes.medium,
                    colors = condoTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            state.errorMessage?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(modifier = Modifier.height(20.dp))
            if (sentTo == null) {
                PrimaryButton(text = "Mandar código", onClick = viewModel::sendCode, loading = state.isSubmitting)
            } else {
                PrimaryButton(
                    text = "Salvar senha nova",
                    onClick = { viewModel.confirm(onPasswordChanged) },
                    loading = state.isSubmitting
                )
                TextButton(onClick = viewModel::sendCode, enabled = !state.isSubmitting, modifier = Modifier.fillMaxWidth()) {
                    Text("Não chegou? Mandar outro código", color = MaterialTheme.colorScheme.tertiary)
                }
            }
        }
    }
}
