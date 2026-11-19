package com.example.condo_mini.data.pdf

import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import java.io.File

/**
 * Marca no topo dos PDFs de demonstração: a da administradora (logo já guardada no
 * aparelho, ou as iniciais) ou, sem administradora, o monograma do Mini Condo.
 */
class PdfBrand(val name: String, val initials: String, val logo: File?) {
    companion object {
        val MiniCondo = PdfBrand("Mini Condo", "M", null)
    }
}

/** Selo de 52 pt centrado em ([cx], [cy]) sobre o cabeçalho escuro. */
internal fun Canvas.drawBrandSeal(brand: PdfBrand, cx: Float, cy: Float, ring: Int, inner: Int, letter: Int) {
    val bitmap = brand.logo?.let { runCatching { BitmapFactory.decodeFile(it.path) }.getOrNull() }
    if (bitmap != null) {
        // Logo sobre fundo claro, para funcionar com qualquer cor de logo.
        val box = RectF(cx - 26f, cy - 26f, cx + 26f, cy + 26f)
        drawRoundRect(box, 8f, 8f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() })
        val scale = minOf(44f / bitmap.width, 44f / bitmap.height)
        val w = bitmap.width * scale
        val h = bitmap.height * scale
        drawBitmap(bitmap, null, RectF(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2), Paint(Paint.FILTER_BITMAP_FLAG))
        bitmap.recycle()
        return
    }
    drawCircle(cx, cy, 26f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ring; style = Paint.Style.STROKE; strokeWidth = 1.2f })
    drawCircle(cx, cy, 22f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = inner })
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = letter
        textSize = if (brand.initials.length > 1) 17f else 22f
        typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }
    drawText(brand.initials, cx, cy + paint.textSize * 0.36f, paint)
}
