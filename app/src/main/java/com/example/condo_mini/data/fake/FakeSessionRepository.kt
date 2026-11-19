package com.example.condo_mini.data.fake

import com.example.condo_mini.domain.model.Brand
import com.example.condo_mini.domain.model.User
import com.example.condo_mini.domain.model.UserRole
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
        val branded = user.copy(brand = demoBrand(user))
        _currentUser.value = branded
        return Result.success(branded)
    }

    // As duas administradoras da demonstração, como no seed da API (lib/setup.php), sem logo.
    private fun demoBrand(user: User): Brand = when {
        user.role == UserRole.ADMIN || user.condoId in setOf("c1", "c2") -> PRIME
        else -> HORIZONTE
    }

    private val PRIME = Brand("co1", "Prime Administradora", color = "#1F3A5F")
    private val HORIZONTE = Brand("co2", "Horizonte Gestão Condominial", color = "#2E6B4F")

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
