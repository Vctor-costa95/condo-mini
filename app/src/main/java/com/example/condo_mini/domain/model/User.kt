package com.example.condo_mini.domain.model

/** Perfis de acesso do app. O [DOORMAN] ainda não tem telas próprias. */
enum class UserRole(val label: String) {
    ADMIN("Administrador"),
    MANAGER("Síndico"),
    DOORMAN("Porteiro"),
    RESIDENT("Morador")
}

data class User(
    val id: String,
    val name: String,
    val unit: String,
    val role: UserRole,
    val email: String = "",
    val phone: String = "",
    /** Condomínio do cadastro; vazio para o administrador, que gerencia vários. */
    val condoId: String = ""
)
