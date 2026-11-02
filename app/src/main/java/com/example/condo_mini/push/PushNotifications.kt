package com.example.condo_mini.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.condo_mini.BuildConfig
import com.example.condo_mini.MainActivity
import com.example.condo_mini.R
import com.example.condo_mini.data.remote.ApiClient
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Notificações push pelo Firebase Cloud Messaging. A configuração vem do
 * google-services.json (ver app/build.gradle.kts); sem ela, tudo aqui é inerte.
 */
object PushNotifications {

    private const val TAG = "CondoPush"
    const val CHANNEL_ID = "condomini"
    const val EXTRA_TAB = "push_tab"

    val isConfigured: Boolean get() = BuildConfig.FIREBASE_APP_ID.isNotBlank()

    /** Aba pedida por uma notificação tocada; a tela principal navega e limpa. */
    private val _pendingTab = MutableStateFlow<String?>(null)
    val pendingTab: StateFlow<String?> = _pendingTab.asStateFlow()

    fun init(context: Context) {
        if (!isConfigured) return
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(
                context,
                FirebaseOptions.Builder()
                    .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                    .setApiKey(BuildConfig.FIREBASE_API_KEY)
                    .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                    .setGcmSenderId(BuildConfig.FIREBASE_SENDER_ID)
                    .build()
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Condomínio", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Encomendas, avisos, reservas e ocorrências"
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    /** Liga este aparelho à sessão logada, para a API saber para onde mandar. */
    suspend fun registerDevice(api: ApiClient) {
        if (!isConfigured || api.token == null) return
        runCatching { sendToken(api, currentToken()) }
            .onFailure { Log.w(TAG, "Não foi possível registrar o aparelho", it) }
    }

    suspend fun sendToken(api: ApiClient, token: String) {
        api.send("POST", "devices", JSONObject().put("token", token).put("platform", "android"))
    }

    private suspend fun currentToken(): String = suspendCancellableCoroutine { cont ->
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { cont.resume(it) }
            .addOnFailureListener { cont.resumeWithException(it) }
    }

    /** Com o app aberto o sistema não mostra a notificação sozinho: mostramos aqui. */
    fun show(context: Context, title: String, body: String, tab: String?) {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(EXTRA_TAB, tab)
        val pending = PendingIntent.getActivity(
            context, (title + body).hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify((title + body).hashCode(), notification) }
            .onFailure { Log.w(TAG, "Sem permissão para notificar", it) }
    }

    /**
     * Notificação tocada: com o app fechado, o Firebase entrega os dados como extras
     * do Intent ("tab"); com o app aberto, vem de [show] em [EXTRA_TAB].
     */
    fun handleIntent(intent: Intent?) {
        val tab = intent?.getStringExtra(EXTRA_TAB) ?: intent?.getStringExtra("tab")
        if (!tab.isNullOrBlank()) _pendingTab.value = tab
    }

    fun consumePendingTab() {
        _pendingTab.value = null
    }
}
