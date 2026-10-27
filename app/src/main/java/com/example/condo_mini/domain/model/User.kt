package com.example.condo_mini.domain.model

/**
 * Perfis de acesso do app. Esta primeira versão implementa apenas a
 * experiência do [RESIDENT]; os demais perfis ganham telas próprias depois.
 */
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
    val phone: String = ""
)
