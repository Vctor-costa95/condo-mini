package com.example.condo_mini.data.remote

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Sessão salva no aparelho para o app abrir já logado (e receber notificações).
 * Token, cadastro e apartamento escolhido ficam cifrados com uma chave AES do
 * Android Keystore, que nunca sai do aparelho; o arquivo fica fora do backup.
 */
class SessionStore(context: Context) {

    data class Saved(val token: String, val user: JSONObject, val unit: String)

    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun save(token: String, user: JSONObject, unit: String) {
        val plain = JSONObject().put("token", token).put("user", user).put("unit", unit).toString()
        runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
            val payload = cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            prefs.edit().putString(KEY, Base64.encodeToString(payload, Base64.NO_WRAP)).apply()
        }.onFailure { Log.w(TAG, "Não foi possível salvar a sessão", it) }
    }

    /** null quando não há sessão ou ela não pode ser lida (chave trocada, arquivo restaurado de outro aparelho). */
    fun load(): Saved? {
        val stored = prefs.getString(KEY, null) ?: return null
        return runCatching {
            val payload = Base64.decode(stored, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, payload, 0, IV_SIZE))
            }
            val json = JSONObject(String(cipher.doFinal(payload, IV_SIZE, payload.size - IV_SIZE), Charsets.UTF_8))
            Saved(json.getString("token"), json.getJSONObject("user"), json.optString("unit"))
        }.onFailure {
            Log.w(TAG, "Sessão salva ilegível; entrando de novo", it)
            clear()
        }.getOrNull()
    }

    fun clear() {
        prefs.edit().remove(KEY).apply()
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).apply {
            init(
                KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
        }.generateKey()
    }

    private companion object {
        const val TAG = "CondoSession"
        const val FILE = "session"
        const val KEY = "session"
        const val KEY_ALIAS = "condomini-session"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
    }
}
