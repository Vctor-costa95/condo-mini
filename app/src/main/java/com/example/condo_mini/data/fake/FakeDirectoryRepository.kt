package com.example.condo_mini.data.fake

import com.example.condo_mini.domain.model.CondoUnit
import com.example.condo_mini.domain.model.Condominium
import com.example.condo_mini.domain.model.Tower
import com.example.condo_mini.domain.model.User
import com.example.condo_mini.domain.model.UserRole
import com.example.condo_mini.domain.model.withUnits
import com.example.condo_mini.domain.repository.DirectoryRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.text.Normalizer
import java.util.UUID

/** Três condomínios de formatos bem diferentes; o primeiro é o dos dados das outras telas. */
class FakeDirectoryRepository : DirectoryRepository {

    private val condos = MutableStateFlow(
        listOf(
            Condominium(
                id = DEFAULT_CONDO_ID,
                name = "Residencial Jardins",
                address = "Rua Oscar Freire, 1200 · Jardins, São Paulo",
                towers = listOf(
                    Tower("c1-a", "Bloco A", floors = 14, unitsPerFloor = 4, floorOverrides = mapOf(14 to 2)),
                    Tower("c1-b", "Bloco B", floors = 12, unitsPerFloor = 6, floorOverrides = mapOf(0 to 2)),
                    Tower("c1-c", "Bloco C", floors = 10, unitsPerFloor = 2)
                )
            ),
            Condominium(
                id = "c2",
                name = "Edifício Vista Mar",
                address = "Av. Atlântica, 3400 · Copacabana, Rio de Janeiro",
                towers = listOf(
                    Tower("c2-t", "Torre única", floors = 20, unitsPerFloor = 2, floorOverrides = mapOf(1 to 0, 20 to 1))
                )
            ),
            Condominium(
                id = "c3",
                name = "Parque das Águas",
                address = "Alameda das Palmeiras, 88 · Alphaville, Barueri",
                towers = (1..4).map { Tower("c3-t$it", "Torre $it", floors = 8, unitsPerFloor = 10) }
            )
        )
    )

    private val _selectedCondoId = MutableStateFlow(DEFAULT_CONDO_ID)
    override val selectedCondoId: StateFlow<String> = _selectedCondoId.asStateFlow()

    private val users = MutableStateFlow(
        listOf(
            // Caso raro, mas real: a Ana também é dona da cobertura do mesmo bloco.
            resident("u1", "Ana Ribeiro", "Apto 101 - Bloco A")
                .withUnits(listOf("Apto 101 - Bloco A", "Apto 1402 - Bloco A")),
            resident("u2", "Paulo Mendes", "Apto 104 - Bloco A"),
            resident("u3", "Clara Mendes", "Apto 104 - Bloco A"),
            resident("u4", "Marina Costa", "Apto 302 - Bloco B"),
            resident("u5", "Ricardo Alves", "Apto 503 - Bloco B"),
            resident("u6", "Helena Rocha", "Apto 201 - Bloco C"),
            resident("u7", "Fernanda Lopes", "Apto 203 - Bloco A"),
            resident("u8", "Gustavo Prado", "Apto 402 - Bloco C"),
            resident("u9", "Beatriz Nunes", ""),
            resident("u10", "Lucas Ferreira", ""),
            User("u11", "Carlos Andrade", "Apto 401 - Bloco A", UserRole.MANAGER, "carlos.andrade@email.com", "(11) 98811-2040", DEFAULT_CONDO_ID),
            User("u12", "José Silva", "", UserRole.DOORMAN, "portaria.jardins@condomini.app", "(11) 3030-1000", DEFAULT_CONDO_ID),
            User("u13", "Antônio Lima", "", UserRole.DOORMAN, "portaria.jardins@condomini.app", "(11) 3030-1000", DEFAULT_CONDO_ID),
            resident("u20", "Sofia Bragança", "Apto 2001 - Torre única", "c2"),
            resident("u21", "Henrique Vasconcelos", "Apto 1502 - Torre única", "c2"),
            User("u22", "Marcelo Tavares", "Apto 801 - Torre única", UserRole.MANAGER, "marcelo.tavares@email.com", "(21) 99820-1144", "c2"),
            resident("u30", "Juliana Matos", "Apto 507 - Torre 2", "c3"),
            resident("u31", "Rafael Couto", "Apto 110 - Torre 4", "c3"),
            resident("u32", "Camila Duarte", "", "c3"),
            // A administradora atende todos os condomínios.
            User("u99", "Renata Prime", "", UserRole.ADMIN, "renata@primeadm.com.br", "(11) 97700-1122")
        )
    )

    override fun observeCondos(): Flow<List<Condominium>> = condos

    override fun selectCondo(condoId: String) {
        _selectedCondoId.value = condoId
    }

    override suspend fun saveCondo(condo: Condominium): Condominium {
        delay(250)
        val saved = if (condo.id.isBlank()) condo.copy(id = UUID.randomUUID().toString()) else condo
        condos.update { list ->
            if (list.any { it.id == saved.id }) list.map { if (it.id == saved.id) saved else it } else list + saved
        }
        // Apartamentos que deixaram de existir saem do cadastro de quem morava neles.
        val labels = saved.units.map { it.label }.toSet()
        users.update { list ->
            list.map { if (it.condoId == saved.id) it.withUnits(it.units.filter { unit -> unit in labels }) else it }
        }
        return saved
    }

    override fun observeUnits(): Flow<List<CondoUnit>> =
        combine(condos, _selectedCondoId) { list, id -> list.firstOrNull { it.id == id }?.units.orEmpty() }

    override fun observeUsers(): Flow<List<User>> =
        combine(users, _selectedCondoId) { list, id ->
            list.filter { it.condoId == id || it.role == UserRole.ADMIN }
        }

    override fun observeUnitsOf(condoId: String): Flow<List<CondoUnit>> =
        condos.map { list -> list.firstOrNull { it.id == condoId }?.units.orEmpty() }

    override fun observeUsersOf(condoId: String): Flow<List<User>> =
        users.map { list -> list.filter { it.condoId == condoId } }

    override suspend fun saveUser(user: User): User {
        delay(250)
        var saved = if (user.id.isBlank()) user.copy(id = UUID.randomUUID().toString()) else user
        if (saved.condoId.isBlank() && saved.role != UserRole.ADMIN) saved = saved.copy(condoId = _selectedCondoId.value)
        users.update { list ->
            if (list.any { it.id == saved.id }) list.map { if (it.id == saved.id) saved else it } else list + saved
        }
        return saved
    }

    override suspend fun deleteUser(userId: String) {
        users.update { list -> list.filterNot { it.id == userId } }
    }

    override suspend fun assignUnit(userId: String, unit: String) {
        users.update { list -> list.map { if (it.id == userId) it.withUnits(it.units + unit) else it } }
    }

    override suspend fun removeUnit(userId: String, unit: String) {
        users.update { list -> list.map { if (it.id == userId) it.withUnits(it.units - unit) else it } }
    }

    override fun findUser(name: String, role: UserRole): User? {
        val needle = name.normalized()
        if (needle.isBlank()) return null
        val candidates = users.value.filter { it.role == role }
        return candidates.firstOrNull { it.name.normalized() == needle }
            ?: candidates.filter { it.name.normalized().substringBefore(" ") == needle }.singleOrNull()
    }

    private fun String.normalized(): String =
        Normalizer.normalize(trim().lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")

    private fun resident(id: String, name: String, unit: String, condoId: String = DEFAULT_CONDO_ID): User {
        val email = name.lowercase()
            .replace("ô", "o").replace("é", "e").replace("á", "a").replace("ç", "c")
            .replace(" ", ".") + "@email.com"
        return User(id, name, unit, UserRole.RESIDENT, email, "(11) 9${id.drop(1).padStart(4, '0')}-0000", condoId)
    }

    companion object {
        const val DEFAULT_CONDO_ID = "c1"
    }
}
