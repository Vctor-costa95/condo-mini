package com.example.condo_mini.ui.screens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.condo_mini.data.AppContainer
import com.example.condo_mini.data.remote.userMessage
import com.example.condo_mini.domain.model.User
import com.example.condo_mini.domain.repository.SessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val login: String = "",
    val password: String = "",
    val showPassword: Boolean = false,
    val isLoggedIn: Boolean = false,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null
)

class LoginViewModel(
    private val sessionRepository: SessionRepository = AppContainer.sessionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onLoginChanged(value: String) {
        _uiState.update { it.copy(login = value, errorMessage = null) }
    }

    fun onPasswordChanged(value: String) {
        _uiState.update { it.copy(password = value, errorMessage = null) }
    }

    fun onTogglePassword() {
        _uiState.update { it.copy(showPassword = !it.showPassword) }
    }

    /** Chama [onSuccess] depois que o servidor aceita o login. */
    fun login(onSuccess: () -> Unit) {
        val currentState = _uiState.value
        if (currentState.isSubmitting) return
        if (currentState.login.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Informe seu usuário para entrar.") }
            return
        }
        _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
        viewModelScope.launch {
            sessionRepository.login(currentState.login, currentState.password)
                .onSuccess {
                    _uiState.update { it.copy(isLoggedIn = true, isSubmitting = false, password = "") }
                    onSuccess()
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isSubmitting = false, errorMessage = e.userMessage()) }
                }
        }
    }

    /** Retomada uma vez, ao abrir: o aparelho continua logado para receber notificações. */
    val restoredSession: Boolean = sessionRepository.restore()

    val currentUser: StateFlow<User?> = sessionRepository.currentUser

    /** O servidor encerrou a sessão (senha trocada, cadastro excluído): explica no login. */
    fun onSessionEnded() {
        _uiState.update { LoginUiState(errorMessage = "Sua sessão foi encerrada. Entre de novo.") }
    }

    /** Depois do login: quem tem mais de um apartamento escolhe na tela seguinte. */
    fun needsUnitChoice(): Boolean =
        sessionRepository.currentUser.value?.hasManyUnits == true

    fun logout() {
        sessionRepository.logout()
        _uiState.update { LoginUiState() }
    }
}
