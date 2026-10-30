package app.energymonitor.data.auth

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Додає Bearer-токен до заголовків HTTP-запитів та ініціює скидання сесії,
 * якщо сервер відхилив авторизований запит із кодом 401 Unauthorized.
 */
class AuthInterceptor(private val sessionManager: SessionManager) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val token = sessionManager.token
        val request = if (token != null && original.header("Authorization") == null) {
            original.newBuilder().header("Authorization", "Bearer $token").build()
        } else {
            original
        }

        val response = chain.proceed(request)
        if (response.code == 401 && token != null) {
            sessionManager.expire("Сесію завершено: токен недійсний або прострочений. Увійдіть знову")
        }
        return response
    }
}
