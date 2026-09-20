package app.energymonitor.domain

/**
 * Результат перевірки форми.
 *
 * @param isSuccess true, якщо перевірка пройдена
 * @param message текст повідомлення для користувача
 */
data class ValidationResult(
    val isSuccess: Boolean,
    val message: String
)

object AuthValidator {

    private const val MIN_LOGIN_LENGTH = 3
    private const val MIN_PASSWORD_LENGTH = 6

    /** Спрощена перевірка адреси електронної пошти без сторонніх бібліотек. */
    fun isEmailValid(email: String): Boolean {
        val trimmed = email.trim()
        val at = trimmed.indexOf('@')
        val dot = trimmed.lastIndexOf('.')
        return at > 0 && dot > at + 1 && dot < trimmed.length - 1 && !trimmed.contains(' ')
    }

    /** Пароль вважається надійним, якщо містить і літери, і цифри. */
    fun isPasswordStrong(password: String): Boolean =
        password.length >= MIN_PASSWORD_LENGTH &&
            password.any { it.isDigit() } &&
            password.any { it.isLetter() }

    /** Перевірка форми авторизації оператора системи моніторингу. */
    fun validateLogin(login: String, password: String): ValidationResult = when {
        login.isBlank() || password.isBlank() ->
            ValidationResult(false, "Заповніть усі поля")
        login.trim().length < MIN_LOGIN_LENGTH ->
            ValidationResult(false, "Логін має містити щонайменше $MIN_LOGIN_LENGTH символи")
        password.length < MIN_PASSWORD_LENGTH ->
            ValidationResult(false, "Пароль має містити щонайменше $MIN_PASSWORD_LENGTH символів")
        else ->
            ValidationResult(true, "Авторизація успішна. Вітаємо, ${login.trim()}!")
    }

    /** Перевірка форми реєстрації нового оператора. */
    fun validateRegistration(
        name: String,
        email: String,
        password: String,
        confirmPassword: String
    ): ValidationResult = when {
        name.isBlank() || email.isBlank() || password.isBlank() || confirmPassword.isBlank() ->
            ValidationResult(false, "Заповніть усі поля")
        name.trim().length < 2 ->
            ValidationResult(false, "Вкажіть коректне ім'я")
        !isEmailValid(email) ->
            ValidationResult(false, "Некоректна адреса електронної пошти")
        !isPasswordStrong(password) ->
            ValidationResult(false, "Пароль: мін. $MIN_PASSWORD_LENGTH символів, літери та цифри")
        password != confirmPassword ->
            ValidationResult(false, "Паролі не співпадають")
        else ->
            ValidationResult(true, "Обліковий запис створено. Доступ до системи надано.")
    }
}
