package com.example.condo_mini.data.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Erro devolvido pela API ({"error": "..."}); a mensagem já vem pronta para o usuário. */
class ApiException(message: String, val status: Int) : IOException(message)

/**
 * Cliente HTTP da API do Condo-mini. Guarda o token da sessão, avisa as listas
 * abertas quando algo muda ([changes]) e publica falhas de ações sem retorno em [errors].
 */
class ApiClient(baseUrl: String, private val context: Context) {

    private val base: HttpUrl = baseUrl.trimEnd('/').toHttpUrl()

    @Volatile
    var token: String? = null

    /** Também usado pelo Coil: as fotos da API exigem o mesmo token. */
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request()
            val current = token
            if (current != null && request.url.host == base.host && request.header("Authorization") == null) {
                chain.proceed(request.newBuilder().header("Authorization", "Bearer $current").build())
            } else {
                chain.proceed(request)
            }
        }
        .build()

    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val changes: SharedFlow<Unit> = _changes.asSharedFlow()

    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    /** O servidor recusou o token (sessão encerrada ou cadastro excluído): o app volta ao login. */
    private val _unauthorized = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val unauthorized: SharedFlow<Unit> = _unauthorized.asSharedFlow()

    val cache = ApiCache(context)

    /**
     * Sem conexão com a API. [since] é o horário (epoch ms) dos dados mais antigos
     * mostrados a partir do [cache]; null quando não havia nada guardado.
     */
    data class Offline(val since: Long?)

    /** null enquanto a API responde; volta a null na primeira resposta do servidor. */
    private val _offline = MutableStateFlow<Offline?>(null)
    val offline: StateFlow<Offline?> = _offline.asStateFlow()

    init {
        // Internet de volta: as listas abertas recarregam na hora, sem esperar os 30 s.
        context.getSystemService(ConnectivityManager::class.java)?.registerDefaultNetworkCallback(
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    if (_offline.value != null) _changes.tryEmit(Unit)
                }
            }
        )
    }

    fun url(path: String, query: Map<String, String?> = emptyMap()): HttpUrl =
        base.newBuilder().apply {
            path.trim('/').split('/').filter { it.isNotEmpty() }.forEach { addPathSegment(it) }
            query.forEach { (key, value) -> if (!value.isNullOrEmpty()) addQueryParameter(key, value) }
        }.build()

    /**
     * Leitura com a última resposta guardada no aparelho: sem conexão, devolve o que
     * veio da última vez e marca [offline]; sem nada guardado, a falha segue.
     */
    suspend fun get(path: String, query: Map<String, String?> = emptyMap()): String {
        val url = url(path, query)
        return try {
            execute(Request.Builder().url(url).get().build(), notify = false)
                .also { body -> withContext(Dispatchers.IO) { cache.write(url, body) } }
        } catch (e: IOException) {
            if (e is ApiException) throw e
            val cached = withContext(Dispatchers.IO) { cache.read(url) }
            if (cached == null) {
                _offline.update { it ?: Offline(since = null) }
                throw e
            }
            _offline.update { Offline(since = minOf(it?.since ?: cached.savedAt, cached.savedAt)) }
            cached.body
        }
    }

    /** POST, PUT, PATCH ou DELETE com corpo JSON; avisa as listas abertas para recarregar. */
    suspend fun send(method: String, path: String, body: JSONObject? = null, query: Map<String, String?> = emptyMap()): String {
        val requestBody = (body ?: JSONObject()).toString().toRequestBody(JSON)
        return execute(Request.Builder().url(url(path, query)).method(method, requestBody).build(), notify = true)
    }

    /** Encerra [token] no servidor; a sessão local já foi esquecida, então ele vai explícito. */
    suspend fun logout(token: String) {
        val request = Request.Builder().url(url("auth/logout"))
            .header("Authorization", "Bearer $token")
            .post(JSONObject().toString().toRequestBody(JSON))
            .build()
        withContext(Dispatchers.IO) { http.newCall(request).execute().close() }
    }

    /** Envia o arquivo de [sourceUri] (content:// ou file://) em multipart, no campo "file". */
    suspend fun upload(path: String, sourceUri: String, fields: Map<String, String>): String {
        val (bytes, name, mime) = withContext(Dispatchers.IO) { readSource(sourceUri) }
        val body = MultipartBody.Builder().setType(MultipartBody.FORM).apply {
            fields.forEach { (key, value) -> addFormDataPart(key, value) }
            addFormDataPart("file", name, bytes.toRequestBody(mime.toMediaType()))
        }.build()
        return execute(Request.Builder().url(url(path)).post(body).build(), notify = true)
    }

    /** Baixa [url] para [target]; `false` quando a API responde 404. */
    suspend fun download(url: HttpUrl, target: File): Boolean = withContext(Dispatchers.IO) {
        http.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
            _offline.value = null
            if (response.code == 404) return@use false
            if (!response.isSuccessful) throw failure(response)
            target.parentFile?.mkdirs()
            // Baixa num temporário: se a conexão cair no meio, o PDF guardado antes continua inteiro.
            val temp = File(target.path + ".part")
            response.body!!.byteStream().use { input -> temp.outputStream().use { input.copyTo(it) } }
            if (!temp.renameTo(target)) {
                target.delete()
                temp.renameTo(target)
            }
            true
        }
    }

    /**
     * Para ações sem retorno (cancelar, excluir...): uma falha vira mensagem em
     * [errors] em vez de derrubar a tela que chamou.
     */
    suspend fun quietly(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            report(e)
        }
    }

    fun report(e: Throwable) {
        Log.w(TAG, "Falha na API", e)
        _errors.tryEmit(e.userMessage())
    }

    /**
     * Lista que se mantém em dia: carrega ao abrir, depois de qualquer alteração
     * feita pelo app e a cada [POLL_MS] (o que outra pessoa mudou).
     */
    fun <T> live(load: suspend () -> T): Flow<T> = channelFlow {
        val wake = Channel<Unit>(Channel.CONFLATED)
        launch { changes.collect { wake.trySend(Unit) } }
        while (true) {
            try {
                send(load())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Falha ao atualizar lista", e)
            }
            withTimeoutOrNull(POLL_MS) { wake.receive() }
        }
    }.distinctUntilChanged()

    private suspend fun execute(request: Request, notify: Boolean): String = withContext(Dispatchers.IO) {
        http.newCall(request).execute().use { response ->
            _offline.value = null
            if (!response.isSuccessful) throw failure(response)
            response.body?.string().orEmpty()
        }.also { if (notify) _changes.tryEmit(Unit) }
    }

    private fun failure(response: Response): ApiException {
        if (response.code == 401 && response.request.header("Authorization") != null) _unauthorized.tryEmit(Unit)
        val message = runCatching { JSONObject(response.body?.string().orEmpty()).getString("error") }.getOrNull()
        return ApiException(message ?: "O servidor não respondeu como esperado (${response.code}).", response.code)
    }

    private fun readSource(sourceUri: String): Triple<ByteArray, String, String> {
        val uri = Uri.parse(sourceUri)
        val resolver = context.contentResolver
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IOException("Não foi possível ler o arquivo.")
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: uri.lastPathSegment ?: "arquivo"
        val mime = resolver.getType(uri) ?: when (name.substringAfterLast('.', "").lowercase()) {
            "pdf" -> "application/pdf"
            "png" -> "image/png"
            "webp" -> "image/webp"
            else -> "image/jpeg"
        }
        return Triple(bytes, name, mime)
    }

    companion object {
        private const val TAG = "CondoApi"
        private const val POLL_MS = 30_000L
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}

/** Texto para o usuário: o da API, ou um aviso de conexão. */
fun Throwable.userMessage(): String = when (this) {
    is ApiException -> message.orEmpty()
    is IOException -> "Sem conexão com o servidor. Confira a internet e tente de novo."
    else -> message ?: "Algo deu errado. Tente de novo."
}
