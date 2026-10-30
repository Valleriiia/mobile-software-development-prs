package app.energymonitor.data.auth

import app.energymonitor.data.network.ApiExecutor
import app.energymonitor.data.network.InvalidResponseException
import app.energymonitor.data.safeCall

class AuthRepository(
    private val api: AuthApiService,
    private val executor: ApiExecutor,
    private val sessionManager: SessionManager
) {

    suspend fun login(login: String, password: String): Result<Session> = safeCall {
        val response = executor.execute("POST /auth/login") {
            api.login(LoginRequest(login.trim(), password))
        }
        startSession(response)
    }

    suspend fun register(name: String, email: String, password: String): Result<Session> = safeCall {
        val response = executor.execute("POST /auth/register") {
            api.register(RegisterRequest(name.trim(), email.trim(), password))
        }
        startSession(response)
    }

    fun logout() = sessionManager.logout()

    private fun startSession(response: TokenResponse): Session {
        val token = response.token ?: throw InvalidResponseException("Сервер не повернув токен")
        return sessionManager.start(token) ?: throw InvalidResponseException("Токен має невірний формат")
    }
}
