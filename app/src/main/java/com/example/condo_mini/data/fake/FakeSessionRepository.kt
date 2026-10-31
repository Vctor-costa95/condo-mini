package com.example.condo_mini.data.fake

import com.example.condo_mini.domain.model.User
import com.example.condo_mini.domain.repository.DirectoryRepository
import com.example.condo_mini.domain.repository.SessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Login contra o cadastro feito pela administração: perfil e apartamentos vêm de lá. */
class FakeSessionRepository(
    private val directoryRepository: DirectoryRepository
) : SessionRepository {

    private val _currentUser = MutableStateFlow<User?>(null)
    override val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    override fun login(login: String, password: String): Boolean {
        val user = directoryRepository.authenticate(login, password) ?: return false
        _currentUser.value = user
        return true
    }

    override fun selectUnit(unit: String) {
        _currentUser.update { user -> user?.takeIf { unit in it.units }?.copy(unit = unit) ?: user }
    }

    override fun logout() {
        _currentUser.value = null
    }

    override fun updateContact(email: String, phone: String) {
        _currentUser.update { it?.copy(email = email.trim(), phone = phone.trim()) }
    }

    companion object {
        /** Apartamento principal da conta "morador"; os dados de exemplo usam o mesmo. */
        const val DEFAULT_UNIT = "Apto 101 - Bloco A"
    }
}
