package com.example.condo_mini.domain.model

import java.time.LocalDateTime
import java.time.YearMonth

/** Um condomínio administrado. As unidades saem das [towers]. */
data class Condominium(
    val id: String,
    val name: String,
    val address: String,
    val towers: List<Tower>
) {
    val units: List<CondoUnit> get() = towers.flatMap { it.units() }
}

/**
 * Bloco ou torre. Cada andar tem [unitsPerFloor] apartamentos, exceto os listados
 * em [floorOverrides] (andar → quantidade), como uma cobertura com 1 ou 2.
 */
data class Tower(
    val id: String,
    val name: String,
    val floors: Int,
    val unitsPerFloor: Int,
    val floorOverrides: Map<Int, Int> = emptyMap()
) {
    fun unitsOn(floor: Int): Int = floorOverrides[floor] ?: unitsPerFloor

    val totalUnits: Int get() = (1..floors).sumOf { unitsOn(it) }

    /** Numeração usual: andar + posição com dois dígitos (1004 = 10º andar, apto 4). */
    fun units(): List<CondoUnit> = (1..floors).flatMap { floor ->
        (1..unitsOn(floor)).map { position ->
            val number = "$floor${position.toString().padStart(2, '0')}"
            CondoUnit(id = "$id-$number", towerId = id, tower = name, floor = floor, number = number)
        }
    }
}

/** Apartamento do condomínio. [label] é o mesmo texto guardado em [User.unit]. */
data class CondoUnit(
    val id: String,
    val towerId: String,
    val tower: String,
    val floor: Int,
    val number: String
) {
    val label: String get() = "Apto $number - $tower"
}

/** Boleto em PDF enviado pela administração para uma unidade. */
data class UnitDocument(
    val id: String,
    val unit: String,
    val reference: YearMonth,
    val fileName: String,
    val filePath: String,
    val sizeBytes: Long,
    val uploadedAt: LocalDateTime,
    val condoId: String = ""
)
