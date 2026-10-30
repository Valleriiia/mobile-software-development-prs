package app.energymonitor.data.auth

import app.energymonitor.domain.UserRole
import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.google.gson.annotations.SerializedName

/** Вміст (payload) JWT: хто користувач, яка в нього роль і до якого часу діє токен. */
internal data class JwtPayload(
    @SerializedName("sub") val sub: String?,
    @SerializedName("name") val name: String?,
    @SerializedName("role") val role: String?,
    @SerializedName("iat") val iat: Long?,
    @SerializedName("exp") val exp: Long?
)

data class JwtClaims(
    val subject: String,
    val name: String?,
    val role: UserRole?,
    val issuedAtSeconds: Long,
    val expiresAtSeconds: Long
) {
    fun isExpired(nowMillis: Long): Boolean = expiresAtSeconds * 1000 <= nowMillis
}

/**
 * JWT має вигляд header.payload.signature, де кожна частина закодована Base64url.
 * Клієнт підпис перевірити не може (секретний ключ є лише на сервері), тому
 * довіряє токену рівно настільки, щоб показати роль і час життя; справжню
 * перевірку виконує сервер за кожним запитом.
 */
object Jwt {

    private val gson = Gson()

    fun decode(token: String): JwtClaims? {
        val parts = token.split('.')
        if (parts.size != 3) return null
        return try {
            val json = String(Base64Url.decode(parts[1]), Charsets.UTF_8)
            val payload = gson.fromJson(json, JwtPayload::class.java) ?: return null
            JwtClaims(
                subject = payload.sub ?: return null,
                name = payload.name,
                role = UserRole.fromClaim(payload.role),
                issuedAtSeconds = payload.iat ?: 0L,
                expiresAtSeconds = payload.exp ?: return null
            )
        } catch (e: IllegalArgumentException) {
            null
        } catch (e: JsonParseException) {
            null
        }
    }
}
