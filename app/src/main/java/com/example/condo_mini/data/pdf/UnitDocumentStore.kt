package com.example.condo_mini.data.pdf

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.IOException
import java.time.YearMonth

/**
 * Copia o PDF escolhido pelo administrador para `filesDir/bills/`. No backend real,
 * este passo vira o upload para o Storage e o caminho, a URL devolvida.
 */
class UnitDocumentStore(private val context: Context) {

    data class Stored(val file: File, val displayName: String)

    fun save(unit: String, reference: YearMonth, sourceUri: String): Stored {
        val uri = Uri.parse(sourceUri)
        val resolver = context.contentResolver
        if (resolver.getType(uri)?.contains("pdf") == false) throw IOException("O arquivo precisa ser um PDF.")
        val dir = File(context.filesDir, "bills").apply { mkdirs() }
        val slug = unit.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
        val target = File(dir, "$slug-$reference.pdf")
        resolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { input.copyTo(it) }
        } ?: throw IOException("Não foi possível ler o arquivo.")
        return Stored(target, displayName(uri) ?: target.name)
    }

    fun delete(path: String) {
        File(path).delete()
    }

    private fun displayName(uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
}
