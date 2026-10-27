package com.example.condo_mini.domain.model

import java.time.LocalDateTime
import java.time.YearMonth

/** Apartamento do condomínio. [label] é o mesmo texto guardado em [User.unit]. */
data class CondoUnit(
    val id: String,
    val block: String,
    val number: String
) {
    val label: String get() = "Apto $number - Bloco $block"
}

/** Boleto em PDF enviado pela administração para uma unidade. */
data class UnitDocument(
    val id: String,
    val unit: String,
    val reference: YearMonth,
    val fileName: String,
    val filePath: String,
    val sizeBytes: Long,
    val uploadedAt: LocalDateTime
)
