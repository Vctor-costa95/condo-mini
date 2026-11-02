package com.example.condo_mini.data.remote

import android.content.Context
import android.util.Log
import okhttp3.HttpUrl
import java.io.File
import java.security.MessageDigest

/**
 * Última resposta de cada leitura da API (avisos, boletos, encomendas...), para o
 * app mostrar alguma coisa sem internet. Fica em `noBackupFilesDir`, fora dos
 * backups, e é apagada ao sair da conta: outra pessoa no mesmo celular não vê nada.
 */
class ApiCache(context: Context) {

    data class Entry(val body: String, val savedAt: Long)

    private val dir = File(context.noBackupFilesDir, "api-cache")

    /** PDFs baixados (boletos e documentos), na mesma pasta para sair junto no logout. */
    val pdfDir: File get() = File(dir, "pdfs")

    fun read(url: HttpUrl): Entry? {
        val file = fileFor(url)
        if (!file.exists()) return null
        return runCatching { Entry(file.readText(), file.lastModified()) }.getOrNull()
    }

    fun write(url: HttpUrl, body: String) {
        runCatching {
            dir.mkdirs()
            // Grava num temporário e troca: uma leitura no meio nunca vê arquivo pela metade.
            val target = fileFor(url)
            val temp = File(dir, target.name + ".tmp")
            temp.writeText(body)
            if (!temp.renameTo(target)) {
                target.delete()
                temp.renameTo(target)
            }
        }.onFailure { Log.w(TAG, "Não foi possível guardar a resposta", it) }
    }

    fun clear() {
        dir.deleteRecursively()
    }

    // A URL inclui condomínio e apartamento (?condoId=&unit=), então cada lista tem o seu arquivo.
    private fun fileFor(url: HttpUrl): File {
        val hash = MessageDigest.getInstance("SHA-256").digest(url.toString().toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(dir, "$hash.json")
    }

    private companion object {
        const val TAG = "CondoCache"
    }
}
