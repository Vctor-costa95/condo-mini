package com.example.condo_mini.domain.model

/** Perfis de acesso do app; cada um tem as próprias abas (ver `AppTab.forRole`). */
enum class UserRole(val label: String) {
    ADMIN("Administrador"),
    MANAGER("Síndico"),
    DOORMAN("Porteiro"),
    RESIDENT("Morador")
}

/**
 * [unit] é o apartamento em uso agora (o que o morador está vendo no app);
 * [units] são todos os apartamentos ligados ao cadastro — quase sempre só um.
 */
data class User(
    val id: String,
    val name: String,
    val unit: String,
    val role: UserRole,
    val email: String = "",
    val phone: String = "",
    /** Condomínio do cadastro; vazio para o administrador, que gerencia vários. */
    val condoId: String = "",
    val units: List<String> = listOfNotNull(unit.takeIf { it.isNotBlank() }),
    /** Usuário de acesso, criado pela administração junto com o cadastro. */
    val login: String = "",
    /** Vazia só nas contas de teste. */
    val password: String = ""
) {
    val hasManyUnits: Boolean get() = units.size > 1
}

/** Troca a lista de apartamentos mantendo [User.unit] dentro dela. */
fun User.withUnits(list: List<String>): User {
    val distinct = list.filter { it.isNotBlank() }.distinct()
    return copy(units = distinct, unit = unit.takeIf { it in distinct } ?: distinct.firstOrNull().orEmpty())
}
