package com.example.condo_mini.data.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.File
import java.security.MessageDigest

private val brandLogoLock = Mutex()

/**
 * Logo da administradora guardada no aparelho, para abrir sem internet. A URL muda
 * (`?v=`) quando a logo é trocada, então o arquivo de uma URL nunca fica velho; ao
 * baixar uma nova, as anteriores são apagadas. null sem internet na primeira vez.
 */
suspend fun ApiClient.brandLogo(url: String): File? = brandLogoLock.withLock {
    val httpUrl = url.toHttpUrlOrNull() ?: return@withLock null
    val name = logoFileName(url)
    val target = File(cache.brandDir, name)
    if (target.exists()) return@withLock target
    runCatching {
        if (!download(httpUrl, target)) return@runCatching null
        withContext(Dispatchers.IO) { cache.brandDir.listFiles()?.filter { it.name != name }?.forEach { it.delete() } }
        target
    }.onFailure { Log.w("CondoBrand", "Logo não baixada", it) }.getOrNull()
}

/** Já baixada? Para quem não pode esperar a rede (PDFs). */
fun ApiClient.cachedBrandLogo(url: String?): File? {
    if (url == null) return null
    return File(cache.brandDir, logoFileName(url)).takeIf { it.exists() }
}

private fun logoFileName(url: String): String =
    MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).joinToString("") { "%02x".format(it) } + ".img"
