package com.example.condo_mini.data.fake

import com.example.condo_mini.domain.model.User
import com.example.condo_mini.domain.repository.SessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Login contra o cadastro feito pela administração: perfil e apartamentos vêm de lá. */
class FakeSessionRepository(
    private val directoryRepository: FakeDirectoryRepository
) : SessionRepository {

    private val _currentUser = MutableStateFlow<User?>(null)
    override val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    /** Os dados de demonstração vivem só em memória: sempre começa no login. */
    override fun restore(): Boolean = false

    override suspend fun login(login: String, password: String): Result<User> {
        val user = directoryRepository.authenticate(login, password)
            ?: return Result.failure(IllegalArgumentException("Usuário ou senha incorretos. Confira e tente de novo."))
        _currentUser.value = user
        return Result.success(user)
    }

    override fun selectUnit(unit: String) {
        _currentUser.update { user -> user?.takeIf { unit in it.units }?.copy(unit = unit) ?: user }
    }

    override fun logout() {
        _currentUser.value = null
    }

    override suspend fun updateContact(email: String, phone: String): Result<User> {
        _currentUser.update { it?.copy(email = email.trim(), phone = phone.trim()) }
        return _currentUser.value?.let { Result.success(it) }
            ?: Result.failure(IllegalStateException("Faça login novamente."))
    }

    companion object {
        /** Apartamento principal da conta "morador"; os dados de exemplo usam o mesmo. */
        const val DEFAULT_UNIT = "Apto 101 - Bloco A"
    }
}
