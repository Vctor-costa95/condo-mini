package com.example.condo_mini

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.condo_mini.push.PushNotifications
import com.example.condo_mini.ui.navigation.AppNavigation
import com.example.condo_mini.ui.theme.CondominiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Aberto ao tocar numa notificação: a tela principal vai para a aba dela.
        if (savedInstanceState == null) PushNotifications.handleIntent(intent)
        setContent {
            CondominiTheme {
                AppNavigation()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        PushNotifications.handleIntent(intent)
    }
}
