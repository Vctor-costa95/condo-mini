package com.example.condo_mini.ui.screens.login

import androidx.lifecycle.ViewModel
import com.example.condo_mini.data.AppContainer
import com.example.condo_mini.domain.repository.SessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class LoginUiState(
    val login: String = "",
    val password: String = "",
    val showPassword: Boolean = false,
    val isLoggedIn: Boolean = false,
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

    fun login(): Boolean {
        val currentState = _uiState.value
        if (currentState.login.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Informe seu usuário para entrar.") }
            return false
        }
        if (!sessionRepository.login(currentState.login, currentState.password)) {
            _uiState.update { it.copy(errorMessage = "Usuário ou senha incorretos. Confira e tente de novo.") }
            return false
        }
        _uiState.update { it.copy(isLoggedIn = true, password = "", errorMessage = null) }
        return true
    }

    /** Depois do login: quem tem mais de um apartamento escolhe na tela seguinte. */
    fun needsUnitChoice(): Boolean =
        sessionRepository.currentUser.value?.hasManyUnits == true

    fun logout() {
        sessionRepository.logout()
        _uiState.update { LoginUiState() }
    }
}
