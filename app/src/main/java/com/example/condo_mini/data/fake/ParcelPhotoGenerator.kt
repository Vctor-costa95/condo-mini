package com.example.condo_mini.data.fake

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import java.io.File

/**
 * Gera "fotos" ilustrativas de encomendas sobre o balcão da portaria.
 * Só para o mock: no app real a foto vem do upload feito pelo porteiro.
 */
class ParcelPhotoGenerator(private val context: Context) {

    enum class Kind { BOX, ENVELOPE }

    fun photo(id: String, kind: Kind, carrier: String, code: String): File {
        val dir = File(context.cacheDir, "parcels").apply { mkdirs() }
        val file = File(dir, "$id.jpg")
        if (file.exists()) return file

        val bitmap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawScene(canvas)
        when (kind) {
            Kind.BOX -> drawBox(canvas, carrier, code)
            Kind.ENVELOPE -> drawEnvelope(canvas, carrier, code)
        }
        drawStamp(canvas)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        bitmap.recycle()
        return file
    }

    private fun drawScene(canvas: Canvas) {
        // Parede
        canvas.drawRect(0f, 0f, W.toFloat(), H * 0.55f, Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, H * 0.55f, 0xFFE9E4DA.toInt(), 0xFFD8D1C4.toInt(), Shader.TileMode.CLAMP)
        })
        // Balcão de madeira
        canvas.drawRect(0f, H * 0.55f, W.toFloat(), H.toFloat(), Paint().apply {
            shader = LinearGradient(0f, H * 0.55f, 0f, H.toFloat(), 0xFF8A6A4A.toInt(), 0xFF5E4630.toInt(), Shader.TileMode.CLAMP)
        })
        val grain = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x22000000; strokeWidth = 2f }
        for (i in 0 until 9) {
            val y = H * 0.6f + i * 38f
            canvas.drawLine(0f, y, W.toFloat(), y + 6f, grain)
        }
        canvas.drawRect(0f, H * 0.55f, W.toFloat(), H * 0.55f + 6f, Paint().apply { color = 0x33000000 })
    }

    private fun drawBox(canvas: Canvas, carrier: String, code: String) {
        val front = RectF(330f, 330f, 870f, 760f)
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x44000000 }
        canvas.drawOval(RectF(300f, 730f, 980f, 800f), shadow)

        // Topo e lateral em perspectiva
        val top = Path().apply {
            moveTo(front.left, front.top); lineTo(front.left + 90f, front.top - 80f)
            lineTo(front.right + 90f, front.top - 80f); lineTo(front.right, front.top); close()
        }
        val side = Path().apply {
            moveTo(front.right, front.top); lineTo(front.right + 90f, front.top - 80f)
            lineTo(front.right + 90f, front.bottom - 80f); lineTo(front.right, front.bottom); close()
        }
        canvas.drawPath(top, fill(0xFFD2A877.toInt()))
        canvas.drawPath(side, fill(0xFFA97E50.toInt()))
        canvas.drawRect(front, fill(0xFFC39463.toInt()))

        // Fita adesiva
        canvas.drawRect(front.centerX() - 30f, front.top, front.centerX() + 30f, front.bottom, fill(0x55F2E6D0))
        val tapeTop = Path().apply {
            moveTo(front.centerX() - 30f, front.top); lineTo(front.centerX() + 60f, front.top - 80f)
            lineTo(front.centerX() + 120f, front.top - 80f); lineTo(front.centerX() + 30f, front.top); close()
        }
        canvas.drawPath(tapeTop, fill(0x55F2E6D0))

        drawLabel(canvas, RectF(front.left + 40f, front.top + 150f, front.left + 250f, front.top + 330f), carrier, code)
    }

    private fun drawEnvelope(canvas: Canvas, carrier: String, code: String) {
        val env = RectF(260f, 400f, 940f, 780f)
        canvas.drawOval(RectF(240f, 760f, 980f, 810f), fill(0x44000000))
        canvas.drawRoundRect(env, 10f, 10f, fill(0xFFF3EEE4.toInt()))
        val flap = Path().apply {
            moveTo(env.left, env.top); lineTo(env.centerX(), env.top + 170f); lineTo(env.right, env.top)
        }
        canvas.drawPath(flap, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFD9D1C2.toInt(); style = Paint.Style.STROKE; strokeWidth = 4f
        })
        drawLabel(canvas, RectF(env.left + 60f, env.bottom - 180f, env.left + 300f, env.bottom - 30f), carrier, code)
    }

    private fun drawLabel(canvas: Canvas, rect: RectF, carrier: String, code: String) {
        canvas.drawRect(rect, fill(0xFFFFFFFF.toInt()))
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF151B26.toInt(); textSize = 26f; typeface = Typeface.DEFAULT_BOLD
        }
        canvas.drawText(carrier.uppercase(), rect.left + 14f, rect.top + 36f, text)
        val small = Paint(text).apply { textSize = 18f; typeface = Typeface.DEFAULT; color = 0xFF5E6675.toInt() }
        canvas.drawText("DEST.: UNIDADE · RET. $code", rect.left + 14f, rect.top + 64f, small)
        // Código de barras da etiqueta
        var x = rect.left + 14f
        var i = 0
        while (x < rect.right - 14f) {
            val w = if ((i * 7 + code.hashCode()) % 3 == 0) 6f else 3f
            if (i % 2 == 0) canvas.drawRect(x, rect.top + 80f, x + w, rect.bottom - 14f, fill(0xFF151B26.toInt()))
            x += w + 2f
            i++
        }
    }

    private fun drawStamp(canvas: Canvas) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xCCFFFFFF.toInt(); textSize = 26f; typeface = Typeface.MONOSPACE
        }
        canvas.drawText("PORTARIA · FOTO DE DEMONSTRAÇÃO", 36f, H - 36f, paint)
    }

    private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }

    private companion object {
        const val W = 1200
        const val H = 900
    }
}
