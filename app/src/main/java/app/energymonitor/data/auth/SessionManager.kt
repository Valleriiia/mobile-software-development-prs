package app.energymonitor.data.auth

import app.energymonitor.domain.UserRole
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

data class Session(
    val token: String,
    val login: String,
    val name: String,
    val role: UserRole,
    val expiresAtMillis: Long
)

sealed interface SessionEvent {
    data class Expired(val message: String) : SessionEvent
}

/**
 * Єдине джерело правди про те, хто зараз увійшов. Екрани підписуються на session,
 * тому вихід або закінчення терміну дії токена автоматично повертає
 * користувача на екран входу з будь-якого місця застосунку.
 */
class SessionManager(
    private val storage: TokenStorage,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val _session = MutableStateFlow(restore())
    val session: StateFlow<Session?> = _session.asStateFlow()

    private val _events = MutableSharedFlow<SessionEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<SessionEvent> = _events.asSharedFlow()

    val token: String? get() = _session.value?.token

    /** Починає сесію за токеном; null, якщо токен пошкоджений або без ролі. */
    fun start(token: String): Session? {
        val session = sessionFromToken(token) ?: return null
        storage.save(token)
        _session.value = session
        return session
    }

    fun logout() {
        storage.clear()
        _session.value = null
    }

    /** Завершує сесію з поясненням: токен прострочений чи відхилений сервером. */
    fun expire(message: String) {
        if (_session.value == null) return
        storage.clear()
        _session.value = null
        _events.tryEmit(SessionEvent.Expired(message))
    }

    private fun restore(): Session? =
        storage.read()
            ?.let { sessionFromToken(it) }
            ?.takeUnless { it.expiresAtMillis <= clock() }

    private fun sessionFromToken(token: String): Session? {
        val claims = Jwt.decode(token) ?: return null
        val role = claims.role ?: return null
        return Session(
            token = token,
            login = claims.subject,
            name = claims.name ?: claims.subject,
            role = role,
            expiresAtMillis = claims.expiresAtSeconds * 1000
        )
    }
}
