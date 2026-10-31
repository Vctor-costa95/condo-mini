package com.example.condo_mini.ui.screens.login

import androidx.lifecycle.ViewModel
import com.example.condo_mini.data.AppContainer
import com.example.condo_mini.domain.model.UserRole
import com.example.condo_mini.domain.repository.SessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class LoginUiState(
    val userName: String = "",
    val unitNumber: String = "",
    // Provisório: depois o perfil virá do cadastro feito pela administradora.
    val role: UserRole = UserRole.RESIDENT,
    val isLoggedIn: Boolean = false,
    val errorMessage: String? = null
)

class LoginViewModel(
    private val sessionRepository: SessionRepository = AppContainer.sessionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onUserNameChanged(newName: String) {
        _uiState.update { it.copy(userName = newName, errorMessage = null) }
    }

    fun onUnitNumberChanged(newUnit: String) {
        _uiState.update { it.copy(unitNumber = newUnit, errorMessage = null) }
    }

    fun onRoleChanged(role: UserRole) {
        _uiState.update { it.copy(role = role, errorMessage = null) }
    }

    fun login(): Boolean {
        val currentState = _uiState.value
        if (currentState.userName.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Por favor, informe seu nome para entrar.") }
            return false
        }
        sessionRepository.login(currentState.userName, currentState.unitNumber, currentState.role)
        _uiState.update { it.copy(isLoggedIn = true, errorMessage = null) }
        return true
    }

    /**
     * Depois do login: quem tem mais de um apartamento e não digitou qual
     * escolhe na tela seguinte.
     */
    fun needsUnitChoice(): Boolean {
        val user = sessionRepository.currentUser.value ?: return false
        return user.hasManyUnits && _uiState.value.unitNumber.trim() !in user.units
    }

    fun logout() {
        sessionRepository.logout()
        _uiState.update {
            it.copy(
                userName = "",
                unitNumber = "",
                isLoggedIn = false,
                errorMessage = null
            )
        }
    }
}
