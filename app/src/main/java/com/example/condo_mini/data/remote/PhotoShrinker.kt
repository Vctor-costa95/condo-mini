package com.example.condo_mini.data.remote

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.math.max

/**
 * Foto da câmera sai com 5–10 MB e passa do limite da API. Antes de enviar, ela
 * vira um JPEG com o lado maior em até [MAX_SIDE] px, já girado conforme o EXIF
 * (o que também tira a localização da foto).
 */
internal object PhotoShrinker {
    private const val MAX_SIDE = 1920
    private const val QUALITY = 82
    private const val SMALL_ENOUGH = 600 * 1024

    /** JPEG reduzido, ou `null` quando a foto já é pequena ou não deu para ler. */
    fun shrink(bytes: ByteArray): ByteArray? {
        if (bytes.size <= SMALL_ENOUGH) return null
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            // Lê já reduzido em potência de 2 para não estourar a memória com fotos de 50 MP.
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
            val decoded = BitmapFactory.decodeByteArray(
                bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample },
            ) ?: return null

            val matrix = Matrix()
            val scale = MAX_SIDE.toFloat() / max(decoded.width, decoded.height)
            if (scale < 1f) matrix.postScale(scale, scale)
            val rotation = exifRotation(bytes)
            if (rotation != 0) matrix.postRotate(rotation.toFloat())
            var bitmap = if (matrix.isIdentity) decoded
            else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
                .also { if (it !== decoded) decoded.recycle() }

            // JPEG não tem transparência: fundo branco em vez de preto.
            if (bitmap.hasAlpha()) {
                val opaque = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
                Canvas(opaque).apply { drawColor(Color.WHITE); drawBitmap(bitmap, 0f, 0f, null) }
                bitmap.recycle()
                bitmap = opaque
            }

            val jpeg = ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
                out.toByteArray()
            }
            bitmap.recycle()
            jpeg.takeIf { it.size < bytes.size }
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            null
        }
    }

    private fun exifRotation(bytes: ByteArray): Int = try {
        when (ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(
            ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL,
        )) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    } catch (e: Exception) {
        0
    }
}
