package com.example.condo_mini.data.fake

import com.example.condo_mini.domain.model.Condominium
import com.example.condo_mini.domain.model.SignupForm
import com.example.condo_mini.domain.repository.SignupRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/** Modo demonstração: os prédios de exemplo; o pedido fica só na memória (não há painel para aceitar). */
class FakeSignupRepository(private val directory: FakeDirectoryRepository) : SignupRepository {

    private val requested = mutableSetOf<String>()

    override suspend fun condos(): Result<List<Condominium>> {
        delay(300)
        return Result.success(directory.observeCondos().first().sortedBy { it.name })
    }

    override suspend fun submit(form: SignupForm): Result<Unit> {
        delay(500)
        val login = form.login.trim().lowercase()
        if (login in requested || !directory.isLoginAvailable(login, "")) {
            return Result.failure(IllegalStateException("Esse usuário já existe. Escolha outro."))
        }
        requested += login
        return Result.success(Unit)
    }
}
