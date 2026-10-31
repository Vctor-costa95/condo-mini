package com.example.condo_mini.domain.model

import java.time.LocalDateTime

/** Tipos de documento do condomínio, na ordem em que aparecem para o morador. */
enum class DocumentCategory(val label: String, val sectionTitle: String) {
    BYLAWS("Regimento", "Regimento interno"),
    CONVENTION("Convenção", "Convenção do condomínio"),
    MINUTES("Ata", "Atas de assembleia"),
    OTHER("Outro", "Outros documentos")
}

/**
 * PDF publicado pelo síndico para todos os moradores do condomínio.
 * [filePath] vazio = documento de demonstração, gerado sob demanda.
 */
data class CondoDocument(
    val id: String,
    val condoId: String,
    val title: String,
    val category: DocumentCategory,
    val fileName: String,
    val filePath: String,
    val sizeBytes: Long,
    val uploadedAt: LocalDateTime,
    val uploadedBy: String
)
