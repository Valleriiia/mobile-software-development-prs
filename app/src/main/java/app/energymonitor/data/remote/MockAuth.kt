package app.energymonitor.data.remote

import app.energymonitor.data.auth.Base64Url
import app.energymonitor.data.auth.Jwt
import app.energymonitor.data.auth.JwtClaims
import app.energymonitor.data.auth.JwtPayload
import app.energymonitor.domain.UserRole
import com.google.gson.Gson
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Результат перевірки заголовка Authorization на стороні сервера. */
sealed interface TokenCheck {
    data class Valid(val claims: JwtClaims) : TokenCheck
    data object Missing : TokenCheck
    data object Invalid : TokenCheck
    data object Expired : TokenCheck
}

/**
 * Серверна частина авторизації мок-бекенду: перелік користувачів, видача та
 * перевірка JWT із підписом HMAC-SHA256. Токен справжній за форматом, тож
 * клієнтський код (декодування, ролі, термін дії) працює так само, як із реальним
 * сервером.
 */
class MockAuth(
    private val secret: String = "mock-secret-key-do-not-use-in-production",
    private val clock: () -> Long = System::currentTimeMillis
) {
    private class User(val login: String, val password: String, val name: String, val role: UserRole)

    private val gson = Gson()
    private val lock = Any()
    private val users = mutableMapOf(
        "admin" to User("admin", "admin123", "Адміністратор", UserRole.ADMIN),
        "operator" to User("operator", "oper123", "Оператор Іваненко", UserRole.USER)
    )

    /** Скільки секунд діятиме наступний виданий токен (налаштовується з панелі розробника). */
    @Volatile
    var tokenTtlSeconds: Long = 300

    fun login(login: String, password: String): String? = synchronized(lock) {
        val user = users[login.trim().lowercase()]
        if (user != null && user.password == password) issueToken(user) else null
    }

    /** Повертає null, якщо користувач із такою поштою вже існує. */
    fun register(name: String, email: String, password: String): String? = synchronized(lock) {
        val key = email.trim().lowercase()
        if (users.containsKey(key)) {
            null
        } else {
            val user = User(key, password, name.trim(), UserRole.USER)
            users[key] = user
            issueToken(user)
        }
    }

    private fun issueToken(user: User): String {
        val nowSeconds = clock() / 1000
        val header = Base64Url.encode("""{"alg":"HS256","typ":"JWT"}""".toByteArray(Charsets.UTF_8))
        val payload = JwtPayload(user.login, user.name, user.role.name, nowSeconds, nowSeconds + tokenTtlSeconds)
        val body = Base64Url.encode(gson.toJson(payload).toByteArray(Charsets.UTF_8))
        return "$header.$body.${sign("$header.$body")}"
    }

    fun verify(authorizationHeader: String?): TokenCheck {
        if (authorizationHeader.isNullOrBlank() || !authorizationHeader.startsWith("Bearer ")) {
            return TokenCheck.Missing
        }
        val token = authorizationHeader.removePrefix("Bearer ").trim()
        val parts = token.split('.')
        if (parts.size != 3) return TokenCheck.Invalid

        // Підпис перераховується з тих самих header.payload: будь-яка підміна ролі
        // чи терміну в payload робить підпис недійсним.
        val expected = sign("${parts[0]}.${parts[1]}")
        if (!MessageDigest.isEqual(expected.toByteArray(Charsets.UTF_8), parts[2].toByteArray(Charsets.UTF_8))) {
            return TokenCheck.Invalid
        }
        val claims = Jwt.decode(token) ?: return TokenCheck.Invalid
        return if (claims.isExpired(clock())) TokenCheck.Expired else TokenCheck.Valid(claims)
    }

    private fun sign(data: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return Base64Url.encode(mac.doFinal(data.toByteArray(Charsets.UTF_8)))
    }
}
