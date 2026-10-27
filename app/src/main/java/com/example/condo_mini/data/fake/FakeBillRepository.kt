package com.example.condo_mini.data.fake

import com.example.condo_mini.data.pdf.BillPdfGenerator
import com.example.condo_mini.domain.model.Bill
import com.example.condo_mini.domain.model.BillStatus
import com.example.condo_mini.domain.model.User
import com.example.condo_mini.domain.repository.BillRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.YearMonth

/** Boletos de exemplo; o PDF é gerado localmente (no backend real, viria pronto da API). */
class FakeBillRepository(
    private val pdfGenerator: BillPdfGenerator
) : BillRepository {

    override fun observeBills(unit: String): Flow<List<Bill>> = flowOf(bills())

    override suspend fun getBill(billId: String, unit: String): Bill? =
        bills().firstOrNull { it.id == billId }

    override suspend fun billPdf(bill: Bill, payer: User): File =
        withContext(Dispatchers.IO) { pdfGenerator.generate(bill, payer) }

    private fun bills(): List<Bill> {
        val today = LocalDate.now()
        val current = YearMonth.now()
        // offset -1 = boleto do próximo mês, já emitido.
        return (-1 until 7).map { offset ->
            val reference = current.minusMonths(offset.toLong())
            val dueDate = reference.atDay(10)
            val status = when {
                offset > 0 -> BillStatus.PAID
                dueDate.isBefore(today) -> BillStatus.OVERDUE
                else -> BillStatus.OPEN
            }
            Bill(
                id = "b$offset",
                reference = reference,
                amount = 685.40 + ((offset + 1) % 3) * 12.5,
                dueDate = dueDate,
                status = status,
                barcode = "34191.79001 01043.510047 91020.150008 ${(8 + offset) % 10} " +
                    "9${reference.monthValue.toString().padStart(2, '0')}0000068540",
                paidAt = if (status == BillStatus.PAID) dueDate.minusDays(2) else null
            )
        }
    }
}
