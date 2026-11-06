package com.example.condo_mini.ui.screens.signup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.condo_mini.data.AppContainer
import com.example.condo_mini.data.remote.userMessage
import com.example.condo_mini.domain.model.Condominium
import com.example.condo_mini.domain.model.SignupForm
import com.example.condo_mini.domain.repository.SignupRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.Normalizer

enum class SignupStep(val number: Int, val title: String) {
    CONDO(1, "Qual é o seu prédio?"),
    UNIT(2, "Qual é o seu apartamento?"),
    DETAILS(3, "Seus dados de acesso"),
    DONE(4, "Pedido enviado")
}

data class SignupUiState(
    val step: SignupStep = SignupStep.CONDO,
    val isLoading: Boolean = true,
    val loadError: String? = null,
    val condos: List<Condominium> = emptyList(),
    val query: String = "",
    val condo: Condominium? = null,
    val unit: String? = null,
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val login: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val showPassword: Boolean = false,
    val isSubmitting: Boolean = false,
    val error: String? = null
) {
    /** Busca sem acento e sem maiúsculas, no nome e no endereço. */
    val visibleCondos: List<Condominium>
        get() {
            val needle = query.normalized()
            if (needle.isEmpty()) return condos
            return condos.filter { it.name.normalized().contains(needle) || it.address.normalized().contains(needle) }
        }
}

private fun String.normalized(): String =
    Normalizer.normalize(trim().lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

class SignupViewModel(
    private val repository: SignupRepository = AppContainer.signupRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SignupUiState())
    val uiState: StateFlow<SignupUiState> = _uiState.asStateFlow()

    init {
        loadCondos()
    }

    fun loadCondos() {
        _uiState.update { it.copy(isLoading = true, loadError = null) }
        viewModelScope.launch {
            repository.condos()
                .onSuccess { list ->
                    // Prédio sem nenhum apartamento cadastrado não tem onde morar ainda.
                    val usable = list.filter { it.units.isNotEmpty() }
                    _uiState.update { it.copy(isLoading = false, condos = usable) }
                }
                .onFailure { e -> _uiState.update { it.copy(isLoading = false, loadError = e.userMessage()) } }
        }
    }

    fun onQueryChanged(value: String) = _uiState.update { it.copy(query = value.take(60)) }

    fun onCondoSelected(condo: Condominium) = _uiState.update {
        it.copy(condo = condo, unit = it.unit.takeIf { unit -> condo.units.any { u -> u.label == unit } }, step = SignupStep.UNIT, error = null)
    }

    fun onUnitSelected(label: String) = _uiState.update { it.copy(unit = label, error = null) }

    fun onUnitConfirmed() = _uiState.update { if (it.unit != null) it.copy(step = SignupStep.DETAILS, error = null) else it }

    fun onNameChanged(value: String) = _uiState.update { it.copy(name = value.take(120), error = null) }
    fun onEmailChanged(value: String) = _uiState.update { it.copy(email = value.trim().take(160), error = null) }
    fun onPhoneChanged(value: String) = _uiState.update { it.copy(phone = value.take(40), error = null) }

    /** Sem espaços e em minúsculas, como o usuário criado pela administradora. */
    fun onLoginChanged(value: String) = _uiState.update { it.copy(login = value.filterNot(Char::isWhitespace).lowercase().take(40), error = null) }

    fun onPasswordChanged(value: String) = _uiState.update { it.copy(password = value.take(72), error = null) }
    fun onConfirmPasswordChanged(value: String) = _uiState.update { it.copy(confirmPassword = value.take(72), error = null) }
    fun onTogglePassword() = _uiState.update { it.copy(showPassword = !it.showPassword) }

    /** Volta um passo; false quando já está no primeiro (a tela sai para o login). */
    fun onBack(): Boolean {
        val state = _uiState.value
        val previous = when (state.step) {
            SignupStep.CONDO, SignupStep.DONE -> return false
            SignupStep.UNIT -> SignupStep.CONDO
            SignupStep.DETAILS -> SignupStep.UNIT
        }
        _uiState.update { it.copy(step = previous, error = null) }
        return true
    }

    fun onSubmit() {
        val state = _uiState.value
        val condo = state.condo ?: return
        val unit = state.unit ?: return
        val error = when {
            state.name.isBlank() -> "Informe seu nome completo."
            state.email.isNotBlank() && !state.email.contains("@") -> "E-mail inválido."
            state.login.length < 3 -> "Crie um usuário com pelo menos 3 letras."
            state.password.length < 6 -> "A senha precisa ter pelo menos 6 caracteres."
            state.password != state.confirmPassword -> "As senhas não são iguais."
            else -> null
        }
        if (error != null) {
            _uiState.update { it.copy(error = error) }
            return
        }
        _uiState.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            repository.submit(
                SignupForm(
                    condoId = condo.id,
                    unit = unit,
                    name = state.name.trim(),
                    email = state.email,
                    phone = state.phone.trim(),
                    login = state.login,
                    password = state.password
                )
            )
                .onSuccess { _uiState.update { it.copy(isSubmitting = false, step = SignupStep.DONE, password = "", confirmPassword = "") } }
                .onFailure { e -> _uiState.update { it.copy(isSubmitting = false, error = e.userMessage()) } }
        }
    }
}
