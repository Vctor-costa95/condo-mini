package com.example.condo_mini.domain.model

import java.time.LocalDate

data class CommonArea(
    val id: String,
    val name: String,
    val description: String,
    val capacity: Int,
    val fee: Double,
    val rules: String
)

enum class ReservationPeriod(val label: String, val hours: String) {
    MORNING("Manhã", "08h às 12h"),
    AFTERNOON("Tarde", "13h às 17h"),
    NIGHT("Noite", "18h às 22h")
}

enum class ReservationStatus(val label: String) {
    CONFIRMED("Confirmada"),
    PENDING("Aguardando aprovação"),
    REJECTED("Recusada"),
    CANCELED("Cancelada")
}

data class Reservation(
    val id: String,
    val area: CommonArea,
    val date: LocalDate,
    val period: ReservationPeriod,
    val unit: String,
    val status: ReservationStatus,
    val residentName: String = "",
    val rejectionReason: String? = null
)
