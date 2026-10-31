package com.example.condo_mini.data.pdf

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.condo_mini.domain.model.CondoDocument
import java.io.File

/**
 * Gera um PDF de DEMONSTRAÇÃO (A4) para os documentos de exemplo do condomínio,
 * com a mesma identidade visual do boleto. Some quando houver backend.
 */
class DocumentPdfGenerator(private val context: Context) {

    // Mesmos tons de docs/design-system.md
    private val ink = 0xFF151B26.toInt()
    private val slate = 0xFF5E6675.toInt()
    private val mist = 0xFF8C93A0.toInt()
    private val hairline = 0xFFE4DED3.toInt()
    private val ivory = 0xFFF7F5F0.toInt()
    private val champagne = 0xFFB08D57.toInt()
    private val champagneLight = 0xFFD9BE8E.toInt()

    fun generate(document: CondoDocument, paragraphs: List<String>): File {
        val pdf = PdfDocument()
        val bodyPaint = text(10.5f, ink)
        val lineHeight = 16f
        val maxWidth = PAGE_W - 2 * M
        val lines = paragraphs.flatMap { wrap(it, bodyPaint, maxWidth) + "" }

        var pageNumber = 0
        var index = 0
        while (index < lines.size || pageNumber == 0) {
            pageNumber++
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_W.toInt(), PAGE_H.toInt(), pageNumber).create())
            val canvas = page.canvas
            canvas.drawColor(0xFFFFFFFF.toInt())
            var y = if (pageNumber == 1) drawHeader(canvas, document) else M + 10f
            while (index < lines.size && y < PAGE_H - FOOTER_H) {
                canvas.drawText(lines[index], M, y, bodyPaint)
                y += lineHeight
                index++
            }
            drawFooter(canvas, pageNumber)
            pdf.finishPage(page)
        }

        val dir = File(context.cacheDir, "documents").apply { mkdirs() }
        val file = File(dir, "${document.id}.pdf")
        file.outputStream().use { pdf.writeTo(it) }
        pdf.close()
        return file
    }

    /** Desenha o cabeçalho e devolve o `y` onde o texto começa. */
    private fun drawHeader(canvas: Canvas, document: CondoDocument): Float {
        canvas.drawRect(0f, 0f, PAGE_W, 104f, fill(ink))
        canvas.drawCircle(M + 26f, 52f, 26f, stroke(champagne, 1.2f))
        canvas.drawCircle(M + 26f, 52f, 22f, fill(0xFF232B3A.toInt()))
        canvas.drawText("C", M + 26f, 60f, text(22f, champagneLight, serif = true, center = true))
        canvas.drawText("Residencial Condo-mini", M + 66f, 48f, text(18f, ivory, serif = true))
        canvas.drawText("Documentos do condomínio", M + 66f, 66f, text(8.5f, 0xFFB7BCC6.toInt()))

        var y = 150f
        canvas.drawText(document.category.sectionTitle.uppercase(), M, y, eyebrow(champagne))
        y += 28f
        val titlePaint = text(20f, ink, serif = true)
        wrap(document.title, titlePaint, PAGE_W - 2 * M).forEach {
            canvas.drawText(it, M, y, titlePaint)
            y += 26f
        }
        canvas.drawLine(M, y, PAGE_W - M, y, stroke(hairline, 1f))
        return y + 30f
    }

    private fun drawFooter(canvas: Canvas, pageNumber: Int) {
        canvas.drawLine(M, PAGE_H - 56f, PAGE_W - M, PAGE_H - 56f, stroke(hairline, 1f))
        canvas.drawText("Documento de demonstração · Condo-mini", M, PAGE_H - 36f, text(8f, mist))
        canvas.drawText("Página $pageNumber", PAGE_W - M, PAGE_H - 36f, text(8f, slate, right = true))
    }

    /** Quebra [text] em linhas que cabem em [maxWidth]. */
    private fun wrap(text: String, paint: Paint, maxWidth: Float): List<String> {
        val lines = mutableListOf<String>()
        var current = ""
        text.split(" ").forEach { word ->
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(candidate) <= maxWidth || current.isEmpty()) {
                current = candidate
            } else {
                lines += current
                current = word
            }
        }
        if (current.isNotEmpty()) lines += current
        return lines
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
        right: Boolean = false,
        center: Boolean = false
    ) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textSize = size
        typeface = Typeface.create(
            if (serif) Typeface.SERIF else Typeface.SANS_SERIF,
            if (bold) Typeface.BOLD else Typeface.NORMAL
        )
        textAlign = when {
            right -> Paint.Align.RIGHT
            center -> Paint.Align.CENTER
            else -> Paint.Align.LEFT
        }
    }

    private fun eyebrow(color: Int) = text(7.5f, color, bold = true).apply { letterSpacing = 0.15f }

    private companion object {
        const val PAGE_W = 595f
        const val PAGE_H = 842f
        const val M = 48f
        const val FOOTER_H = 80f
    }
}
