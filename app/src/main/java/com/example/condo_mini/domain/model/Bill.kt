package com.example.condo_mini.domain.model

import java.time.LocalDate
import java.time.YearMonth

enum class BillStatus(val label: String) {
    OPEN("Em aberto"),
    OVERDUE("Vencido"),
    PAID("Pago")
}

data class Bill(
    val id: String,
    val reference: YearMonth,
    val amount: Double,
    val dueDate: LocalDate,
    val status: BillStatus,
    val barcode: String,
    val paidAt: LocalDate? = null,
    /** Como foi a baixa: pelo arquivo do banco ou na mão pela administração; null nos antigos. */
    val paidVia: PaymentSource? = null
)

enum class PaymentSource(val label: String) {
    BANK_RETURN("pelo banco"),
    MANUAL("pela administração")
}
