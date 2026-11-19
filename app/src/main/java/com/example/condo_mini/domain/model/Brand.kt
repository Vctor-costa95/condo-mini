package com.example.condo_mini.domain.model

/**
 * Marca da administradora do condomínio, que o app mostra no lugar da nossa.
 * Sem [companyId] (cadastro sem administradora) o app fica com a marca Mini Condo.
 */
data class Brand(
    val companyId: String?,
    val name: String,
    /** Imagem pública; muda (`?v=`) a cada troca de logo, então pode ser guardada sem prazo. */
    val logoUrl: String? = null,
    /** Cor da administradora em `#RRGGBB`, usada só em detalhes (fundo das iniciais). */
    val color: String? = null
) {
    val isCompany: Boolean get() = companyId != null

    /** Até duas iniciais do nome, para quando não há logo. */
    val initials: String
        get() = name.split(' ').filter { it.firstOrNull()?.isLetter() == true }
            .take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "MC" }
}
