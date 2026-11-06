package com.example.condo_mini.domain.model

/**
 * Conta criada pelo próprio morador na tela de login. Vira cadastro só quando a
 * administradora aceita no painel web; até lá, o login avisa que está aguardando.
 */
data class SignupForm(
    val condoId: String,
    val unit: String,
    val name: String,
    val email: String,
    val phone: String,
    val login: String,
    val password: String
)
