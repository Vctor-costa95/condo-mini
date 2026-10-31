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

/** Andar 0 é o térreo. */
const val GROUND_FLOOR = 0

/**
 * Bloco ou torre. Cada andar tem [unitsPerFloor] apartamentos, exceto os listados
 * em [floorOverrides] (andar → quantidade): uma cobertura com 2, um andar de lazer
 * com 0. O térreo ([GROUND_FLOOR]) não tem apartamentos, a menos que esteja listado.
 */
data class Tower(
    val id: String,
    val name: String,
    val floors: Int,
    val unitsPerFloor: Int,
    val floorOverrides: Map<Int, Int> = emptyMap()
) {
    /** Quantidade quando o andar não tem exceção. */
    fun defaultUnitsOn(floor: Int): Int = if (floor == GROUND_FLOOR) 0 else unitsPerFloor

    fun unitsOn(floor: Int): Int = floorOverrides[floor] ?: defaultUnitsOn(floor)

    /** Térreo + andares, de baixo para cima. */
    val allFloors: IntRange get() = GROUND_FLOOR..floors

    val totalUnits: Int get() = allFloors.sumOf { unitsOn(it) }

    /**
     * Numeração usual: andar + posição com dois dígitos (1004 = 10º andar, apto 4).
     * No térreo, "T" + posição (T01, T02).
     */
    fun units(): List<CondoUnit> = allFloors.flatMap { floor ->
        (1..unitsOn(floor)).map { position ->
            val suffix = position.toString().padStart(2, '0')
            val number = if (floor == GROUND_FLOOR) "T$suffix" else "$floor$suffix"
            CondoUnit(id = "$id-$number", towerId = id, tower = name, floor = floor, number = number)
        }
    }
}

/** "Térreo", "1º", "14º". */
fun floorLabel(floor: Int): String = if (floor == GROUND_FLOOR) "Térreo" else "${floor}º"

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
