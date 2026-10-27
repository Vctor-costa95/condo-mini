package com.example.condo_mini.data.fake

import com.example.condo_mini.domain.model.CondoUnit
import com.example.condo_mini.domain.model.User
import com.example.condo_mini.domain.model.UserRole
import com.example.condo_mini.domain.repository.DirectoryRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

/** 3 blocos × 5 andares × 4 apartamentos; os nomes batem com os dados das outras telas. */
class FakeDirectoryRepository : DirectoryRepository {

    private val units = MutableStateFlow(
        listOf("A", "B", "C").flatMap { block ->
            (1..5).flatMap { floor ->
                (1..4).map { apt ->
                    val number = "$floor${apt.toString().padStart(2, '0')}"
                    CondoUnit(id = "$block$number", block = block, number = number)
                }
            }
        }
    )

    private val users = MutableStateFlow(
        listOf(
            resident("u1", "Ana Ribeiro", "101", "A"),
            resident("u2", "Paulo Mendes", "104", "A"),
            resident("u3", "Clara Mendes", "104", "A"),
            resident("u4", "Marina Costa", "302", "B"),
            resident("u5", "Ricardo Alves", "503", "B"),
            resident("u6", "Helena Rocha", "201", "C"),
            resident("u7", "Fernanda Lopes", "203", "A"),
            resident("u8", "Gustavo Prado", "402", "C"),
            User("u9", "Carlos Andrade", "Apto 401 - Bloco A", UserRole.MANAGER, "carlos.andrade@email.com", "(11) 98811-2040"),
            User("u10", "José Silva", "", UserRole.DOORMAN, "portaria@condomini.app", "(11) 3030-1000"),
            User("u11", "Antônio Lima", "", UserRole.DOORMAN, "portaria@condomini.app", "(11) 3030-1000"),
            User("u12", "Renata Prime", "", UserRole.ADMIN, "renata@primeadm.com.br", "(11) 97700-1122"),
            // Cadastros novos, ainda sem apartamento.
            User("u13", "Beatriz Nunes", "", UserRole.RESIDENT, "beatriz.nunes@email.com", "(11) 99120-4433"),
            User("u14", "Lucas Ferreira", "", UserRole.RESIDENT, "lucas.ferreira@email.com", "(11) 98456-7788")
        )
    )

    override fun observeUnits(): Flow<List<CondoUnit>> = units

    override fun observeUsers(): Flow<List<User>> = users

    override suspend fun saveUser(user: User): User {
        delay(250)
        val saved = if (user.id.isBlank()) user.copy(id = UUID.randomUUID().toString()) else user
        users.update { list ->
            if (list.any { it.id == saved.id }) list.map { if (it.id == saved.id) saved else it } else list + saved
        }
        return saved
    }

    override suspend fun deleteUser(userId: String) {
        users.update { list -> list.filterNot { it.id == userId } }
    }

    override suspend fun assignUnit(userId: String, unit: String) {
        users.update { list -> list.map { if (it.id == userId) it.copy(unit = unit) else it } }
    }

    private fun resident(id: String, name: String, number: String, block: String): User {
        val email = name.lowercase()
            .replace("ô", "o").replace("é", "e").replace("á", "a")
            .replace(" ", ".") + "@email.com"
        return User(id, name, "Apto $number - Bloco $block", UserRole.RESIDENT, email, "(11) 9${id.drop(1).padStart(4, '0')}-0000")
    }
}
