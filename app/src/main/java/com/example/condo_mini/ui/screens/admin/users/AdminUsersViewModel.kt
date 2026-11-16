package com.example.condo_mini.ui.screens.admin.users

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.condo_mini.data.AppContainer
import com.example.condo_mini.data.remote.userMessage
import com.example.condo_mini.domain.model.CondoUnit
import com.example.condo_mini.domain.model.SignupRequest
import com.example.condo_mini.domain.model.User
import com.example.condo_mini.domain.model.UserRole
import com.example.condo_mini.domain.repository.DirectoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.Normalizer

data class UserForm(
    /** Vazio para um cadastro novo. */
    val id: String = "",
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    /** Usuário de acesso; a senha só vem preenchida quando for trocar. */
    val login: String = "",
    val password: String = "",
    val role: UserRole = UserRole.RESIDENT,
    /** Apartamentos ligados ao cadastro; o primeiro é o principal. */
    val units: List<String> = emptyList(),
    val isSaving: Boolean = false,
    val error: String? = null
) {
    val isNew: Boolean get() = id.isBlank()

    /** Porteiros e administradores não moram no condomínio. */
    val needsUnit: Boolean get() = role == UserRole.RESIDENT || role == UserRole.MANAGER

    /** Toca no apartamento: entra se não estava, sai se estava. */
    fun toggleUnit(label: String): UserForm = copy(units = if (label in units) units - label else units + label)
}

/** Aceitar um pedido: confere os dados e o apartamento (dá para trocar) antes de criar o cadastro. */
data class SignupReview(
    val request: SignupRequest,
    val unit: String = request.unit,
    val isSaving: Boolean = false,
    val error: String? = null
)

/** Recusar um pedido: o motivo aparece para a pessoa quando ela tenta entrar. */
data class SignupRejection(
    val request: SignupRequest,
    val reason: String = "",
    val isSaving: Boolean = false,
    val error: String? = null
)

data class AdminUsersUiState(
    val isLoading: Boolean = true,
    val requests: List<SignupRequest> = emptyList(),
    val review: SignupReview? = null,
    val rejection: SignupRejection? = null,
    val role: UserRole? = null,
    val query: String = "",
    val counts: Map<UserRole?, Int> = emptyMap(),
    val users: List<User> = emptyList(),
    val withoutUnit: Int = 0,
    val units: List<CondoUnit> = emptyList(),
    /** Quem mora em cada apartamento, para o seletor mostrar o nome no lugar de só o número. */
    val occupantsByUnit: Map<String, List<User>> = emptyMap(),
    val form: UserForm? = null,
    val userMessage: String? = null
)

class AdminUsersViewModel(
    private val directoryRepository: DirectoryRepository = AppContainer.directoryRepository
) : ViewModel() {

    private val role = MutableStateFlow<UserRole?>(null)
    private val query = MutableStateFlow("")
    private val form = MutableStateFlow<UserForm?>(null)
    private val userMessage = MutableStateFlow<String?>(null)
    private val review = MutableStateFlow<SignupReview?>(null)
    private val rejection = MutableStateFlow<SignupRejection?>(null)

    private val signups = combine(directoryRepository.observeSignupRequests(), review, rejection, ::Triple)

    val uiState: StateFlow<AdminUsersUiState> = combine(
        combine(directoryRepository.observeUsers(), directoryRepository.observeUnits(), ::Pair),
        signups,
        combine(role, query, ::Pair),
        form,
        userMessage
    ) { (users, units), (requests, review, rejection), (role, query), form, message ->
        val needle = query.normalized()
        AdminUsersUiState(
            isLoading = false,
            requests = requests,
            review = review,
            rejection = rejection,
            role = role,
            query = query,
            counts = mapOf<UserRole?, Int>(null to users.size) + UserRole.entries.associateWith { r -> users.count { it.role == r } },
            users = users
                .filter { role == null || it.role == role }
                .filter { needle.isBlank() || "${it.name} ${it.units.joinToString(" ")} ${it.email} ${it.login}".normalized().contains(needle) }
                // Moradores sem unidade primeiro: são os que pedem ação.
                .sortedWith(compareBy<User>({ !(it.role == UserRole.RESIDENT && it.units.isEmpty()) }, { it.name })),
            withoutUnit = users.count { it.role == UserRole.RESIDENT && it.units.isEmpty() },
            units = units,
            occupantsByUnit = users
                .filter { it.role == UserRole.RESIDENT || it.role == UserRole.MANAGER }
                .flatMap { user -> user.units.map { it to user } }
                .groupBy({ it.first }, { it.second }),
            form = form,
            userMessage = message
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AdminUsersUiState())

    fun onRoleSelected(value: UserRole?) {
        role.value = value
    }

    fun onQueryChanged(value: String) {
        query.value = value
    }

    fun onNewUser() {
        form.value = UserForm(role = role.value ?: UserRole.RESIDENT)
    }

    fun onEditUser(user: User) {
        form.value = UserForm(user.id, user.name, user.email, user.phone, user.login, role = user.role, units = user.units)
    }

    fun onDismissForm() {
        form.value = null
    }

    fun onFormChanged(transform: (UserForm) -> UserForm) {
        form.update { it?.let(transform)?.copy(error = null) }
    }

    fun onSave() {
        val current = form.value ?: return
        val error = when {
            current.name.isBlank() -> "Informe o nome."
            current.email.isNotBlank() && !current.email.contains("@") -> "E-mail inválido."
            current.login.isBlank() -> "Crie um usuário para a pessoa entrar no app."
            current.login.trim().contains(" ") -> "O usuário não pode ter espaços."
            current.isNew && current.password.length < 6 -> "A senha precisa ter pelo menos 6 caracteres."
            !current.isNew && current.password.isNotEmpty() && current.password.length < 6 -> "A senha nova precisa ter pelo menos 6 caracteres."
            else -> null
        }
        if (error != null) {
            form.update { it?.copy(error = error) }
            return
        }
        form.update { it?.copy(isSaving = true) }
        viewModelScope.launch {
            val units = if (current.needsUnit) current.units else emptyList()
            runCatching {
                if (!directoryRepository.isLoginAvailable(current.login, current.id)) {
                    form.update { it?.copy(isSaving = false, error = "Esse usuário já é de outro cadastro.") }
                    return@launch
                }
                directoryRepository.saveUser(
                    User(
                        id = current.id,
                        name = current.name.trim(),
                        unit = units.firstOrNull().orEmpty(),
                        role = current.role,
                        email = current.email.trim(),
                        phone = current.phone.trim(),
                        login = current.login.trim().lowercase(),
                        // Em branco na edição: o repositório mantém a senha atual.
                        password = current.password,
                        units = units
                    )
                )
            }.onSuccess { saved ->
                form.value = null
                userMessage.value = if (current.isNew) "Cadastro criado: ${saved.name}." else "Cadastro de ${saved.name} atualizado."
            }.onFailure { e ->
                form.update { it?.copy(isSaving = false, error = e.userMessage()) }
            }
        }
    }

    fun onDelete() {
        val current = form.value ?: return
        viewModelScope.launch {
            directoryRepository.deleteUser(current.id)
            form.value = null
            userMessage.value = "Cadastro de ${current.name} excluído."
        }
    }

    // ------------------------------------------------------------ pedidos de cadastro

    fun onReviewRequest(request: SignupRequest) {
        review.value = SignupReview(request)
    }

    fun onReviewUnitSelected(unit: String) {
        review.update { it?.copy(unit = unit, error = null) }
    }

    fun onDismissReview() {
        review.value = null
    }

    fun onApproveRequest() {
        val current = review.value ?: return
        review.update { it?.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            directoryRepository.approveSignup(current.request.id, current.unit)
                .onSuccess { user ->
                    review.value = null
                    userMessage.value = "${user.name} já pode entrar no app (${current.unit})."
                }
                .onFailure { e -> review.update { it?.copy(isSaving = false, error = e.userMessage()) } }
        }
    }

    fun onRejectRequest(request: SignupRequest) {
        rejection.value = SignupRejection(request)
    }

    fun onRejectionReasonChanged(reason: String) {
        rejection.update { it?.copy(reason = reason.take(255), error = null) }
    }

    fun onDismissRejection() {
        rejection.value = null
    }

    fun onConfirmRejection() {
        val current = rejection.value ?: return
        if (current.reason.isBlank()) {
            rejection.update { it?.copy(error = "Diga o motivo: a pessoa vê ao tentar entrar.") }
            return
        }
        rejection.update { it?.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            directoryRepository.rejectSignup(current.request.id, current.reason)
                .onSuccess {
                    rejection.value = null
                    userMessage.value = "Pedido de ${current.request.name} recusado."
                }
                .onFailure { e -> rejection.update { it?.copy(isSaving = false, error = e.userMessage()) } }
        }
    }

    fun onMessageShown() {
        userMessage.value = null
    }

    private fun String.normalized(): String =
        Normalizer.normalize(trim().lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")
}
