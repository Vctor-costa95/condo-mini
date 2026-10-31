package com.example.condo_mini.data.fake

import com.example.condo_mini.domain.model.User
import com.example.condo_mini.domain.model.UserRole
import com.example.condo_mini.domain.repository.SessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class FakeSessionRepository : SessionRepository {

    private val _currentUser = MutableStateFlow<User?>(null)
    override val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    override fun login(name: String, unit: String, role: UserRole) {
        _currentUser.value = User(
            id = "user-${role.name.lowercase()}",
            name = name.trim(),
            unit = unit.trim().ifBlank { DEFAULT_UNIT },
            role = role,
            condoId = if (role == UserRole.ADMIN) "" else FakeDirectoryRepository.DEFAULT_CONDO_ID,
            email = "morador@condomini.app",
            phone = "(11) 99999-0000"
        )
    }

    override fun logout() {
        _currentUser.value = null
    }

    override fun updateContact(email: String, phone: String) {
        _currentUser.update { it?.copy(email = email.trim(), phone = phone.trim()) }
    }

    companion object {
        /** Unidade usada quando o campo fica vazio; os dados de exemplo usam a mesma. */
        const val DEFAULT_UNIT = "Apto 101 - Bloco A"
    }
}
