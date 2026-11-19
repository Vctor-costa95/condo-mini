package com.example.condo_mini.data.fake

import com.example.condo_mini.data.pdf.BillPdfGenerator
import com.example.condo_mini.data.pdf.UnitDocumentStore
import com.example.condo_mini.domain.model.Bill
import com.example.condo_mini.domain.model.BillStatus
import com.example.condo_mini.domain.model.PaymentSource
import com.example.condo_mini.domain.model.UnitDocument
import com.example.condo_mini.domain.model.User
import com.example.condo_mini.domain.repository.BillRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.util.UUID

/**
 * Boletos de exemplo. O PDF é o enviado pelo administrador para a unidade, quando
 * existe; senão, um de demonstração gerado localmente.
 */
class FakeBillRepository(
    private val pdfGenerator: BillPdfGenerator,
    private val documentStore: UnitDocumentStore
) : BillRepository {

    private val documents = MutableStateFlow<List<UnitDocument>>(emptyList())

    /** Baixas feitas (ou desfeitas) na demonstração: id → pago (true) ou aberto (false). */
    private val paidOverrides = MutableStateFlow<Map<String, Boolean>>(emptyMap())

    override fun observeBills(unit: String): Flow<List<Bill>> = paidOverrides.map { bills() }

    override suspend fun getBill(billId: String, unit: String): Bill? =
        bills().firstOrNull { it.id == billId }

    override suspend fun setPaid(billId: String, paid: Boolean): Result<Bill> {
        paidOverrides.update { it + (billId to paid) }
        return bills().firstOrNull { it.id == billId }?.let { Result.success(it) }
            ?: Result.failure(IllegalArgumentException("Boleto não encontrado."))
    }

    private fun Bill.withOverride(): Bill = when (paidOverrides.value[id]) {
        true -> copy(status = BillStatus.PAID, paidAt = LocalDate.now(), paidVia = PaymentSource.MANUAL)
        false -> copy(
            status = if (dueDate.isBefore(LocalDate.now())) BillStatus.OVERDUE else BillStatus.OPEN,
            paidAt = null,
            paidVia = null
        )
        null -> this
    }

    override suspend fun billPdf(bill: Bill, payer: User): File = withContext(Dispatchers.IO) {
        documents.value
            .firstOrNull { it.condoId == payer.condoId && it.unit == payer.unit && it.reference == bill.reference }
            ?.let { File(it.filePath) }
            ?.takeIf { it.exists() }
            ?: pdfGenerator.generate(bill, payer)
    }

    override fun observeUnitDocuments(): Flow<List<UnitDocument>> = documents

    override suspend fun uploadUnitDocument(
        condoId: String,
        unit: String,
        reference: YearMonth,
        sourceUri: String
    ): Result<UnitDocument> = withContext(Dispatchers.IO) {
        runCatching {
            val stored = documentStore.save(condoId, unit, reference, sourceUri)
            val document = UnitDocument(
                id = UUID.randomUUID().toString(),
                unit = unit,
                reference = reference,
                fileName = stored.displayName,
                filePath = stored.file.absolutePath,
                sizeBytes = stored.file.length(),
                uploadedAt = LocalDateTime.now(),
                condoId = condoId
            )
            // Um PDF por unidade e mês: o novo substitui o anterior (mesmo arquivo em disco).
            documents.update { list ->
                list.filterNot { it.condoId == condoId && it.unit == unit && it.reference == reference } + document
            }
            document
        }
    }

    override suspend fun deleteUnitDocument(documentId: String) {
        val document = documents.value.firstOrNull { it.id == documentId } ?: return
        withContext(Dispatchers.IO) { documentStore.delete(document.filePath) }
        documents.update { list -> list.filterNot { it.id == documentId } }
    }

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
            ).withOverride()
        }
    }
}
