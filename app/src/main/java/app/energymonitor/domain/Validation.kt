package app.energymonitor.domain

/** Результат будь-якої перевірки форми: успіх/провал і повідомлення для користувача. */
data class ValidationResult(
    val isSuccess: Boolean,
    val message: String
)
