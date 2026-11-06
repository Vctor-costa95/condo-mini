package com.example.condo_mini

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.condo_mini.push.PushNotifications
import com.example.condo_mini.ui.navigation.AppNavigation
import com.example.condo_mini.ui.theme.CondominiTheme
import com.example.condo_mini.ui.theme.ThemeMode
import com.example.condo_mini.ui.theme.ThemePreference

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Aberto ao tocar numa notificação: a tela principal vai para a aba dela.
        if (savedInstanceState == null) PushNotifications.handleIntent(intent)
        setContent {
            val mode by ThemePreference.mode.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (mode) {
                ThemeMode.SYSTEM -> systemDark
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            // Ícones da barra de status e de navegação acompanham o tema do app, não o do celular.
            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(LightScrim, DarkScrim) { darkTheme }
                )
                onDispose {}
            }
            CondominiTheme(darkTheme = darkTheme) {
                AppNavigation()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        PushNotifications.handleIntent(intent)
    }

    private companion object {
        // Os mesmos véus que o enableEdgeToEdge() usa por padrão na barra de navegação de 3 botões.
        val LightScrim = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        val DarkScrim = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}
