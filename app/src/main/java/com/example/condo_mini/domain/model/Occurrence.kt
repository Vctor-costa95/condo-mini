package com.example.condo_mini.domain.model

import java.time.LocalDateTime

enum class OccurrenceCategory(val label: String) {
    NOISE("Barulho"),
    MAINTENANCE("Manutenção"),
    SECURITY("Segurança"),
    GARAGE("Garagem"),
    CLEANING("Limpeza"),
    OTHER("Outros")
}

enum class OccurrenceStatus(val label: String) {
    OPEN("Aberta"),
    IN_REVIEW("Em análise"),
    NEEDS_INFO("Aguardando detalhes"),
    RESOLVED("Resolvida")
}

/** Mensagem na conversa entre morador e administração. */
data class OccurrenceMessage(
    val author: String,
    val fromManagement: Boolean,
    val text: String,
    val sentAt: LocalDateTime,
    val photoUrls: List<String> = emptyList()
)

/** Registro no livro de ocorrências do condomínio. */
data class Occurrence(
    val id: String,
    val protocol: String,
    val title: String,
    val description: String,
    val category: OccurrenceCategory,
    val status: OccurrenceStatus,
    val createdAt: LocalDateTime,
    val unit: String,
    val authorName: String = "",
    /** Fotos anexadas no registro (URL remota ou URI local). */
    val photoUrls: List<String> = emptyList(),
    val messages: List<OccurrenceMessage> = emptyList()
)
