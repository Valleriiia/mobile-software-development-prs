package app.energymonitor.data.auth

import android.content.Context

/** Сховище токена; SessionManager залежить від інтерфейсу, тож у тестах Android не потрібен. */
interface TokenStorage {
    fun read(): String?
    fun save(token: String)
    fun clear()
}

/**
 * Зберігає токен у SharedPreferences для збереження сесії між перезапусками застосунку.
 */
class SharedPreferencesTokenStorage(context: Context) : TokenStorage {

    private val prefs = context.getSharedPreferences("auth", Context.MODE_PRIVATE)

    override fun read(): String? = prefs.getString(KEY_TOKEN, null)

    override fun save(token: String) {
        prefs.edit().putString(KEY_TOKEN, token).apply()
    }

    override fun clear() {
        prefs.edit().remove(KEY_TOKEN).apply()
    }

    private companion object {
        const val KEY_TOKEN = "access_token"
    }
}
