package com.example.condo_mini.domain.model

import java.time.LocalDate

enum class ServiceType(val label: String) {
    MOVE("Mudança"),
    REPAIR("Reparo ou instalação")
}

/** O que será feito no reparo: ajuda a portaria a saber quem vai receber. */
enum class RepairKind(val label: String) {
    INTERNET("Internet"),
    ELECTRICAL("Elétrica"),
    PLUMBING("Hidráulica"),
    OTHER("Outro")
}

/** Mudanças e reparos acontecem em horário comercial: só manhã e tarde. */
val SERVICE_PERIODS = listOf(ReservationPeriod.MORNING, ReservationPeriod.AFTERNOON)

/** Agendamento de mudança ou reparo, sempre aprovado pelo síndico. */
data class ServiceBooking(
    val id: String,
    val type: ServiceType,
    val date: LocalDate,
    val period: ReservationPeriod,
    val unit: String,
    val residentName: String,
    val condoId: String,
    val status: ReservationStatus,
    val repairKind: RepairKind? = null,
    /** Empresa ou técnico que virá; opcional. */
    val provider: String = "",
    val rejectionReason: String? = null
) {
    /** "Mudança" ou "Reparo · Internet". */
    val title: String
        get() = repairKind?.let { "Reparo · ${it.label}" } ?: type.label
}
