package com.example.condo_mini.push

import com.example.condo_mini.data.AppContainer
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Recebe as notificações do Firebase e os tokens novos do aparelho. */
class CondoMessagingService : FirebaseMessagingService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // O Firebase troca o token de tempos em tempos: avisa a API se alguém está logado.
    override fun onNewToken(token: String) {
        val api = AppContainer.apiClient ?: return
        if (api.token == null) return
        scope.launch { runCatching { PushNotifications.sendToken(api, token) } }
    }

    // Só chega aqui com o app aberto; fechado, o sistema mostra a notificação sozinho.
    override fun onMessageReceived(message: RemoteMessage) {
        val notification = message.notification ?: return
        PushNotifications.show(
            context = this,
            title = notification.title.orEmpty(),
            body = notification.body.orEmpty(),
            tab = message.data["tab"]
        )
    }
}
