package com.example.condo_mini.ui.theme

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Tema escolhido em "Meu perfil". [SYSTEM] segue o modo claro/escuro do celular. */
enum class ThemeMode(val label: String) {
    SYSTEM("Sistema"),
    LIGHT("Claro"),
    DARK("Escuro")
}

/**
 * Guarda o tema no aparelho (vale para qualquer conta que entrar nele, inclusive na tela de login).
 * Chamado uma vez em [com.example.condo_mini.CondoApplication.onCreate].
 */
object ThemePreference {
    private const val FILE = "preferences"
    private const val KEY = "theme_mode"

    private var prefs: SharedPreferences? = null
    private val _mode = MutableStateFlow(ThemeMode.SYSTEM)
    val mode: StateFlow<ThemeMode> = _mode.asStateFlow()

    fun init(context: Context) {
        val store = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        prefs = store
        _mode.value = store.getString(KEY, null)
            ?.let { saved -> ThemeMode.entries.firstOrNull { it.name == saved } }
            ?: ThemeMode.SYSTEM
    }

    fun set(mode: ThemeMode) {
        _mode.value = mode
        prefs?.edit()?.putString(KEY, mode.name)?.apply()
    }
}
