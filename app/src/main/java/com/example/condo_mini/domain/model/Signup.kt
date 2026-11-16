package com.example.condo_mini.domain.model

/**
 * Conta criada pelo próprio morador na tela de login. Vira cadastro só quando a
 * administradora aceita no painel web; até lá, o login avisa que está aguardando.
 */
/** Pedido de cadastro aguardando a administradora: vira morador com a senha que a pessoa escolheu. */
data class SignupRequest(
    val id: String,
    val condoId: String,
    val name: String,
    val email: String,
    val phone: String,
    val login: String,
    /** Apartamento que a pessoa escolheu; a administradora pode trocar ao aceitar. */
    val unit: String,
    val createdAt: java.time.LocalDateTime
)

data class SignupForm(
    val condoId: String,
    val unit: String,
    val name: String,
    val email: String,
    val phone: String,
    val login: String,
    val password: String
)
