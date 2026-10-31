package com.example.condo_mini.data.pdf

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.IOException

/**
 * Copia o PDF escolhido pelo síndico para `filesDir/documents/`. No backend real,
 * este passo vira o upload para o Storage e o caminho, a URL devolvida.
 */
class CondoDocumentStore(private val context: Context) {

    data class Stored(val file: File, val displayName: String)

    fun save(condoId: String, documentId: String, sourceUri: String): Stored {
        val uri = Uri.parse(sourceUri)
        val resolver = context.contentResolver
        if (resolver.getType(uri)?.contains("pdf") == false) throw IOException("O arquivo precisa ser um PDF.")
        val dir = File(context.filesDir, "documents/$condoId").apply { mkdirs() }
        val target = File(dir, "$documentId.pdf")
        resolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { input.copyTo(it) }
        } ?: throw IOException("Não foi possível ler o arquivo.")
        return Stored(target, displayName(uri) ?: target.name)
    }

    fun delete(path: String) {
        if (path.isNotBlank()) File(path).delete()
    }

    private fun displayName(uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
}
