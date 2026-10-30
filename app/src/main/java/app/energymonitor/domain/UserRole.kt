package app.energymonitor.domain

/** Відповідає значенню поля "role" у payload токена. */
enum class UserRole(val title: String) {
    ADMIN("Адміністратор"),
    USER("Оператор");

    companion object {
        fun fromClaim(value: String?): UserRole? =
            values().firstOrNull { it.name.equals(value, ignoreCase = true) }
    }
}

/**
 * Права ролей в одному місці. Це лише зручність інтерфейсу (сховати кнопку),
 * а справжнє обмеження виконує сервер: операції запису без ролі ADMIN він
 * відхиляє відповіддю 403, навіть якщо кнопку якось вдалося натиснути.
 */
object Permissions {
    fun canEditReadings(role: UserRole?): Boolean = role == UserRole.ADMIN
    fun canUseDevTools(role: UserRole?): Boolean = role == UserRole.ADMIN
}
