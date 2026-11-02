package com.example.condo_mini.data.remote

import android.util.Log
import com.example.condo_mini.domain.model.CondoUnit
import com.example.condo_mini.domain.model.Condominium
import com.example.condo_mini.domain.model.User
import com.example.condo_mini.domain.model.UserRole
import com.example.condo_mini.domain.repository.DirectoryRepository
import com.example.condo_mini.domain.repository.SessionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * Sessão que sobrevive ao fechar o app: o aparelho continua logado até a pessoa
 * sair ou o servidor recusar o token (ver [SessionStore]).
 */
class ApiSessionRepository(
    private val api: ApiClient,
    private val store: SessionStore,
    /** Depois de entrar ou retomar a sessão: registra o aparelho para notificações. */
    private val onSignedIn: suspend () -> Unit = {}
) : SessionRepository {

    private val _currentUser = MutableStateFlow<User?>(null)
    override val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        scope.launch { api.unauthorized.collect { forget() } }
    }

    /** Abre com o cadastro salvo, sem esperar a rede; depois confere com o servidor. */
    override fun restore(): Boolean {
        val saved = store.load() ?: return false
        api.token = saved.token
        val user = saved.user.toUser()
        _currentUser.value = user.copy(unit = saved.unit.takeIf { it in user.units } ?: user.unit)
        scope.launch {
            // Cadastro pode ter mudado (apartamentos, perfil); um 401 aqui leva de volta ao login.
            runCatching { api.get("me").obj { it.toUser() } }.onSuccess(::keepUnitAndSave)
            onSignedIn()
        }
        return true
    }

    override suspend fun login(login: String, password: String): Result<User> = runCatching {
        val body = JSONObject(
            api.send("POST", "auth/login", JSONObject().put("login", login.trim()).put("password", password))
        )
        api.token = body.getString("token")
        body.getJSONObject("user").toUser().also {
            _currentUser.value = it
            save()
            scope.launch { onSignedIn() }
        }
    }.onFailure { Log.w("CondoApi", "Login falhou", it) }

    override fun selectUnit(unit: String) {
        _currentUser.update { user -> user?.takeIf { unit in it.units }?.copy(unit = unit) ?: user }
        save()
    }

    override fun logout() {
        val token = api.token ?: return
        forget()
        // Invalida o token no servidor sem segurar a tela; o app já esqueceu a sessão.
        scope.launch { runCatching { api.logout(token) } }
    }

    override suspend fun updateContact(email: String, phone: String): Result<User> = runCatching {
        api.send("PATCH", "me", JSONObject().put("email", email.trim()).put("phone", phone.trim()))
            .obj { it.toUser() }
            .let(::keepUnitAndSave)
    }

    /** Mantém o apartamento que o morador escolheu, se ele ainda for dele. */
    private fun keepUnitAndSave(fresh: User): User {
        val current = _currentUser.value ?: return fresh
        return fresh.copy(unit = current.unit.takeIf { it in fresh.units } ?: fresh.unit).also {
            _currentUser.value = it
            save()
        }
    }

    private fun save() {
        val token = api.token ?: return
        val user = _currentUser.value ?: return
        store.save(token, user.toJson(), user.unit)
    }

    private fun forget() {
        api.token = null
        store.clear()
        _currentUser.value = null
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ApiDirectoryRepository(private val api: ApiClient) : DirectoryRepository {

    private val _selectedCondoId = MutableStateFlow("")
    override val selectedCondoId: StateFlow<String> = _selectedCondoId.asStateFlow()

    private val condos: Flow<List<Condominium>> =
        api.live { api.get("condos").list { it.toCondo() } }
            // Sem escolha (ou a escolhida sumiu): fica com o primeiro condomínio.
            .onEach { list ->
                if (list.none { it.id == _selectedCondoId.value }) _selectedCondoId.value = list.firstOrNull()?.id.orEmpty()
            }

    override fun observeCondos(): Flow<List<Condominium>> = condos

    override fun selectCondo(condoId: String) {
        _selectedCondoId.value = condoId
    }

    override suspend fun saveCondo(condo: Condominium): Condominium =
        api.send("POST", "condos", condo.toJson()).obj { it.toCondo() }

    override fun observeUnits(): Flow<List<CondoUnit>> =
        combine(condos, _selectedCondoId) { list, id -> list.firstOrNull { it.id == id }?.units.orEmpty() }

    override fun observeUsers(): Flow<List<User>> =
        _selectedCondoId.flatMapLatest { id -> if (id.isBlank()) flowOf(emptyList()) else usersOf(id) }

    override fun observeUnitsOf(condoId: String): Flow<List<CondoUnit>> =
        condos.map { list -> list.firstOrNull { it.id == condoId }?.units.orEmpty() }

    override fun observeUsersOf(condoId: String): Flow<List<User>> =
        usersOf(condoId).map { list -> list.filter { it.condoId == condoId } }

    private fun usersOf(condoId: String): Flow<List<User>> =
        api.live { api.get("users", mapOf("condoId" to condoId)).list { it.toUser() } }

    override suspend fun saveUser(user: User): User {
        val body = JSONObject()
            .put("id", user.id)
            .put("condoId", user.condoId.ifBlank { _selectedCondoId.value })
            .put("name", user.name)
            .put("role", user.role.name)
            .put("email", user.email)
            .put("phone", user.phone)
            .put("login", user.login)
            .put("units", JSONArray(if (user.role == UserRole.ADMIN) emptyList() else user.units))
        // Em branco na edição: o servidor mantém a senha atual.
        if (user.password.isNotEmpty()) body.put("password", user.password)
        return api.send("POST", "users", body).obj { it.toUser() }
    }

    override suspend fun deleteUser(userId: String) = api.quietly {
        api.send("DELETE", "users/$userId")
    }

    override suspend fun assignUnit(userId: String, unit: String) = api.quietly {
        api.send("POST", "users/$userId/units", JSONObject().put("unit", unit))
    }

    override suspend fun removeUnit(userId: String, unit: String) = api.quietly {
        api.send("DELETE", "users/$userId/units", query = mapOf("unit" to unit))
    }

    override suspend fun isLoginAvailable(login: String, exceptUserId: String): Boolean =
        JSONObject(api.get("logins/available", mapOf("login" to login.trim(), "exceptUserId" to exceptUserId)))
            .getBoolean("available")
}
