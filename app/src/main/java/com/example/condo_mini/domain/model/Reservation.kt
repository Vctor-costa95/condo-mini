package com.example.condo_mini.domain.model

import java.time.LocalDate

/** Como o espaço é reservado; a administração escolhe ao criar o espaço. */
enum class BookingMode(val label: String, val description: String, val hint: String) {
    PERIOD("Por turno", "Manhã, tarde ou noite", "Manhã, tarde, noite"),
    HOURLY("Por hora", "Início e duração, dentro do horário de funcionamento", "Início e duração"),
    DAILY("Dia inteiro", "Uma reserva por dia, do começo ao fim", "Uma por dia")
}

data class CommonArea(
    val id: String,
    val name: String,
    val description: String,
    val capacity: Int,
    /** Fixa por reserva, nos dois modos. Com taxa, a reserva espera o síndico. */
    val fee: Double,
    val rules: String,
    /** Chave do ícone escolhido pela administração (ver `AreaIcon`). */
    val iconKey: String = "",
    /** Motivo da interdição; `null` quando o espaço está liberado. */
    val closedReason: String? = null,
    val condoId: String = "",
    val bookingMode: BookingMode = BookingMode.PERIOD,
    /** Funcionamento, em horas cheias (só no modo por hora): das [opensAt] às [closesAt]. */
    val opensAt: Int = 8,
    val closesAt: Int = 22,
    /** Máximo de horas seguidas numa reserva (só no modo por hora). */
    val maxHours: Int = 2
) {
    val isClosed: Boolean get() = closedReason != null

    val isHourly: Boolean get() = bookingMode == BookingMode.HOURLY

    val isDaily: Boolean get() = bookingMode == BookingMode.DAILY

    /** "7h às 22h" */
    val openingHours: String get() = "${opensAt}h às ${closesAt}h"
}

enum class ReservationPeriod(val label: String, val hours: String, val startHour: Int, val endHour: Int) {
    MORNING("Manhã", "08h às 12h", 8, 12),
    AFTERNOON("Tarde", "13h às 17h", 13, 17),
    NIGHT("Noite", "18h às 22h", 18, 22)
}

/** O horário pedido: um turno inteiro, horas cheias de [startHour] até [endHour] ou o dia todo. */
sealed interface ReservationSlot {
    data class Period(val period: ReservationPeriod) : ReservationSlot
    data class Hours(val startHour: Int, val endHour: Int) : ReservationSlot

    /** Ocupa das 0h às 24h, então colide com qualquer outra reserva da data. */
    data object FullDay : ReservationSlot {
        const val START_HOUR = 0
        const val END_HOUR = 24
    }
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
    /** null nas reservas por hora e nas do dia inteiro. */
    val period: ReservationPeriod?,
    val unit: String,
    val status: ReservationStatus,
    val residentName: String = "",
    val rejectionReason: String? = null,
    val startHour: Int = period?.startHour ?: 0,
    val endHour: Int = period?.endHour ?: 0
) {
    val isFullDay: Boolean
        get() = period == null && startHour == ReservationSlot.FullDay.START_HOUR && endHour == ReservationSlot.FullDay.END_HOUR

    /** "14h às 16h", ou "Dia inteiro" */
    val hoursLabel: String get() = if (isFullDay) "Dia inteiro" else "${startHour}h às ${endHour}h"

    /** Curto, para frases: "Tarde", "14h às 16h" ou "Dia inteiro". */
    val shortLabel: String get() = period?.label ?: hoursLabel

    /** Completo, para cartões: "Tarde · 13h às 17h", "14h às 16h" ou "Dia inteiro". */
    val timeLabel: String get() = period?.let { "${it.label} · ${it.hours}" } ?: hoursLabel
}
