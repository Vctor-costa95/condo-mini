package com.example.condo_mini.data.pdf

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.condo_mini.domain.model.Bill
import com.example.condo_mini.domain.model.BillStatus
import com.example.condo_mini.domain.model.User
import java.io.File
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Gera um boleto de DEMONSTRAÇÃO em PDF (A4) com a identidade visual do app.
 * Substituir pelo PDF oficial do banco/administradora quando houver backend.
 */
class BillPdfGenerator(private val context: Context) {

    private val ptBr = Locale.forLanguageTag("pt-BR")
    private val currency = NumberFormat.getCurrencyInstance(ptBr)
    private val date = DateTimeFormatter.ofPattern("dd/MM/yyyy", ptBr)
    private val month = DateTimeFormatter.ofPattern("MMMM 'de' yyyy", ptBr)

    // Mesmos tons de docs/design-system.md
    private val ink = 0xFF151B26.toInt()
    private val slate = 0xFF5E6675.toInt()
    private val mist = 0xFF8C93A0.toInt()
    private val hairline = 0xFFE4DED3.toInt()
    private val linen = 0xFFF0ECE4.toInt()
    private val ivory = 0xFFF7F5F0.toInt()
    private val champagne = 0xFFB08D57.toInt()
    private val champagneLight = 0xFFD9BE8E.toInt()
    private val emerald = 0xFF2E6A4F.toInt()
    private val burgundy = 0xFF9A2E2E.toInt()

    fun generate(bill: Bill, payer: User): File {
        val document = PdfDocument()
        val page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, 1).create())
        draw(page.canvas, bill, payer)
        document.finishPage(page)

        val dir = File(context.cacheDir, "bills").apply { mkdirs() }
        val file = File(dir, "boleto-${bill.reference}.pdf")
        file.outputStream().use { document.writeTo(it) }
        document.close()
        return file
    }

    private fun draw(canvas: Canvas, bill: Bill, payer: User) {
        canvas.drawColor(0xFFFFFFFF.toInt())
        val reference = bill.reference.format(month).replaceFirstChar { it.titlecase(ptBr) }

        // Cabeçalho escuro com monograma
        canvas.drawRect(0f, 0f, PAGE_W.toFloat(), 104f, fill(ink))
        canvas.drawCircle(M + 26f, 52f, 26f, stroke(champagne, 1.2f))
        canvas.drawCircle(M + 26f, 52f, 22f, fill(0xFF232B3A.toInt()))
        canvas.drawText("C", M + 26f, 60f, text(22f, champagneLight, serif = true, center = true))
        canvas.drawText("Residencial Condo-mini", M + 66f, 48f, text(18f, ivory, serif = true))
        canvas.drawText("CNPJ 12.345.678/0001-90 · Administração Condo-mini", M + 66f, 66f, text(8.5f, 0xFFB7BCC6.toInt()))
        canvas.drawText("BOLETO DE CONDOMÍNIO", PAGE_W - M, 44f, eyebrow(champagneLight, right = true))
        canvas.drawText(reference, PAGE_W - M, 66f, text(15f, ivory, serif = true, right = true))

        var y = 140f

        // Selo de status
        val (statusText, statusColor) = when (bill.status) {
            BillStatus.PAID -> "PAGO EM ${bill.paidAt?.format(date).orEmpty()}" to emerald
            BillStatus.OVERDUE -> "VENCIDO · SUJEITO A MULTA E JUROS" to burgundy
            BillStatus.OPEN -> "EM ABERTO" to champagne
        }
        canvas.drawText(statusText, M, y, eyebrow(statusColor))
        y += 26f

        // Beneficiário e pagador
        val colW = (PAGE_W - 2 * M - 16f) / 2
        party(canvas, M, y, "Beneficiário", "Condomínio Residencial Condo-mini", "Banco 341 · Ag. 0104 · C/C 35100-4")
        party(canvas, M + colW + 16f, y, "Pagador", payer.name.ifBlank { "Morador" }, payer.unit)
        y += 70f

        // Grade de dados principais
        val cells = listOf(
            "Vencimento" to bill.dueDate.format(date),
            "Valor do documento" to currency.format(bill.amount),
            "Nosso número" to "109/${bill.reference.monthValue.toString().padStart(2, '0')}${bill.reference.year}-7",
            "Competência" to reference
        )
        val cellW = (PAGE_W - 2 * M) / cells.size
        canvas.drawRoundRect(RectF(M, y, PAGE_W - M, y + 62f), 10f, 10f, stroke(hairline, 1f))
        cells.forEachIndexed { index, (label, value) ->
            val x = M + index * cellW
            if (index > 0) canvas.drawLine(x, y + 12f, x, y + 50f, stroke(hairline, 1f))
            canvas.drawText(label.uppercase(ptBr), x + 14f, y + 24f, eyebrow(mist))
            canvas.drawText(value, x + 14f, y + 44f, text(12f, ink, bold = index == 1))
        }
        y += 96f

        // Discriminação
        canvas.drawText("DISCRIMINAÇÃO", M, y, eyebrow(slate))
        y += 14f
        val reserve = 56.0
        val ordinary = 560.0
        val water = bill.amount - ordinary - reserve
        listOf(
            "Taxa condominial ordinária" to ordinary,
            "Fundo de reserva (10%)" to reserve,
            "Consumo de água individualizado" to water
        ).forEach { (label, value) ->
            y += 26f
            canvas.drawText(label, M, y, text(11f, ink))
            canvas.drawText(currency.format(value), PAGE_W - M, y, text(11f, ink, right = true))
            canvas.drawLine(M, y + 10f, PAGE_W - M, y + 10f, stroke(hairline, 0.8f))
        }
        y += 36f
        canvas.drawText("Total", M, y, text(13f, ink, serif = true))
        canvas.drawText(currency.format(bill.amount), PAGE_W - M, y, text(15f, ink, serif = true, right = true))
        y += 44f

        // Linha digitável + código de barras
        canvas.drawRoundRect(RectF(M, y, PAGE_W - M, y + 132f), 12f, 12f, fill(linen))
        canvas.drawText("LINHA DIGITÁVEL", M + 18f, y + 24f, eyebrow(slate))
        canvas.drawText(bill.barcode, M + 18f, y + 46f, text(11.5f, ink, mono = true))
        drawBarcode(canvas, bill.barcode, M + 18f, y + 62f, PAGE_W - M - 18f, 52f)
        y += 160f

        // Instruções
        canvas.drawText("INSTRUÇÕES", M, y, eyebrow(slate))
        listOf(
            "Após o vencimento, multa de 2% e juros de 1% ao mês, conforme convenção.",
            "Pague pelo app do seu banco usando a linha digitável ou o código de barras.",
            "Dúvidas: administração pelo app Condo-mini, aba Ocorrências."
        ).forEach {
            y += 18f
            canvas.drawText("·  $it", M, y, text(9.5f, slate))
        }

        // Marca d'água de demonstração
        canvas.save()
        canvas.rotate(-30f, PAGE_W / 2f, PAGE_H / 2f)
        canvas.drawText(
            "DEMONSTRAÇÃO",
            PAGE_W / 2f,
            PAGE_H / 2f + 40f,
            text(72f, 0x14151B26, serif = true, center = true)
        )
        canvas.restore()

        // Rodapé
        canvas.drawLine(M, PAGE_H - 56f, PAGE_W - M, PAGE_H - 56f, stroke(hairline, 1f))
        canvas.drawText(
            "Documento de demonstração gerado pelo app Condo-mini. Sem valor para pagamento.",
            M,
            PAGE_H - 36f,
            text(8.5f, mist)
        )
    }

    private fun party(canvas: Canvas, x: Float, y: Float, label: String, name: String, detail: String) {
        canvas.drawText(label.uppercase(ptBr), x, y, eyebrow(mist))
        canvas.drawText(name, x, y + 20f, text(12.5f, ink, bold = true))
        canvas.drawText(detail, x, y + 37f, text(10f, slate))
    }

    /** Barras ilustrativas derivadas dos dígitos (não é um código ITF válido). */
    private fun drawBarcode(canvas: Canvas, code: String, left: Float, top: Float, right: Float, height: Float) {
        val digits = code.filter { it.isDigit() }.map { it - '0' }
        val widths = digits.flatMap { d -> listOf(1 + d % 3, 1 + (d + 1) % 2) }
        val unit = (right - left) / widths.sum()
        var x = left
        widths.forEachIndexed { index, w ->
            if (index % 2 == 0) canvas.drawRect(x, top, x + w * unit, top + height, fill(ink))
            x += w * unit
        }
    }

    private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }

    private fun stroke(color: Int, width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.STROKE
        strokeWidth = width
    }

    private fun text(
        size: Float,
        color: Int,
        serif: Boolean = false,
        bold: Boolean = false,
        mono: Boolean = false,
        right: Boolean = false,
        center: Boolean = false
    ) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textSize = size
        typeface = Typeface.create(
            when {
                mono -> Typeface.MONOSPACE
                serif -> Typeface.SERIF
                else -> Typeface.SANS_SERIF
            },
            if (bold) Typeface.BOLD else Typeface.NORMAL
        )
        textAlign = when {
            right -> Paint.Align.RIGHT
            center -> Paint.Align.CENTER
            else -> Paint.Align.LEFT
        }
    }

    private fun eyebrow(color: Int, right: Boolean = false) =
        text(7.5f, color, bold = true, right = right).apply { letterSpacing = 0.15f }

    private companion object {
        const val PAGE_W = 595 // A4 em pontos
        const val PAGE_H = 842
        const val M = 40f
    }
}
