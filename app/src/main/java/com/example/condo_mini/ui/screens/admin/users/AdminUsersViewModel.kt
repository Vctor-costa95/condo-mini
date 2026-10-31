package com.example.condo_mini.ui.screens.admin.users

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.condo_mini.data.AppContainer
import com.example.condo_mini.domain.model.CondoUnit
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

data class AdminUsersUiState(
    val isLoading: Boolean = true,
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

    val uiState: StateFlow<AdminUsersUiState> = combine(
        directoryRepository.observeUsers(),
        directoryRepository.observeUnits(),
        combine(role, query, ::Pair),
        form,
        userMessage
    ) { users, units, (role, query), form, message ->
        val needle = query.normalized()
        AdminUsersUiState(
            isLoading = false,
            role = role,
            query = query,
            counts = mapOf<UserRole?, Int>(null to users.size) + UserRole.entries.associateWith { r -> users.count { it.role == r } },
            users = users
                .filter { role == null || it.role == role }
                .filter { needle.isBlank() || "${it.name} ${it.units.joinToString(" ")} ${it.email}".normalized().contains(needle) }
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
        form.value = UserForm(user.id, user.name, user.email, user.phone, user.role, user.units)
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
            else -> null
        }
        if (error != null) {
            form.update { it?.copy(error = error) }
            return
        }
        form.update { it?.copy(isSaving = true) }
        viewModelScope.launch {
            val units = if (current.needsUnit) current.units else emptyList()
            val saved = directoryRepository.saveUser(
                User(
                    id = current.id,
                    name = current.name.trim(),
                    unit = units.firstOrNull().orEmpty(),
                    role = current.role,
                    email = current.email.trim(),
                    phone = current.phone.trim(),
                    units = units
                )
            )
            form.value = null
            userMessage.value = if (current.isNew) "Cadastro criado: ${saved.name}." else "Cadastro de ${saved.name} atualizado."
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

    fun onMessageShown() {
        userMessage.value = null
    }

    private fun String.normalized(): String =
        Normalizer.normalize(trim().lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")
}
