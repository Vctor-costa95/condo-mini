package com.example.condo_mini.data.fake

import com.example.condo_mini.domain.model.Condominium
import com.example.condo_mini.domain.model.SignupForm
import com.example.condo_mini.domain.model.SignupRequest
import com.example.condo_mini.domain.repository.SignupRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime
import java.util.UUID

/** Modo demonstração: os prédios de exemplo; o pedido vai para a lista que o administrador vê em Cadastros. */
class FakeSignupRepository(private val directory: FakeDirectoryRepository) : SignupRepository {

    override suspend fun condos(): Result<List<Condominium>> {
        delay(300)
        return Result.success(directory.observeCondos().first().sortedBy { it.name })
    }

    override suspend fun submit(form: SignupForm): Result<Unit> {
        delay(500)
        val login = form.login.trim().lowercase()
        if (directory.hasSignupRequest(login) || !directory.isLoginAvailable(login, "")) {
            return Result.failure(IllegalStateException("Esse usuário já existe. Escolha outro."))
        }
        directory.addSignupRequest(
            SignupRequest(
                id = UUID.randomUUID().toString(),
                condoId = form.condoId,
                name = form.name.trim(),
                email = form.email.trim(),
                phone = form.phone.trim(),
                login = login,
                unit = form.unit,
                createdAt = LocalDateTime.now()
            ),
            form.password
        )
        return Result.success(Unit)
    }
}
