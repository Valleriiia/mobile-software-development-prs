package app.energymonitor.data.error

/**
 * Типи помилок, які застосунок розрізняє, і текст для користувача до кожного.
 * Решта коду оперує цими типами, а не сирими винятками чи HTTP-кодами, тому
 * текст повідомлення й реакцію на помилку можна змінити в одному місці.
 */
sealed class AppError(val message: String) {

    data object NoNetwork : AppError("Немає підключення до мережі. Показано збережені дані")

    data object Timeout : AppError("Сервер не відповів вчасно. Спробуйте ще раз пізніше")

    data object Unauthorized : AppError("Сесію завершено. Увійдіть знову")

    data object Forbidden : AppError("Недостатньо прав для цієї дії")

    data object NotFound : AppError("Запитаний запис не знайдено")

    data object TooManyRequests : AppError("Забагато запитів. Зачекайте хвилину")

    /** 400 або 422: сервер відхилив дані. */
    class Validation(serverMessage: String?) : AppError(serverMessage ?: "Сервер відхилив надіслані дані")

    /** 409: запис уже існує або конфліктує з наявним. */
    class Conflict(serverMessage: String?) : AppError(serverMessage ?: "Такі дані вже існують")

    class Server(val code: Int) : AppError("Помилка сервера ($code). Спробуйте пізніше")

    class InvalidData(detail: String?) : AppError(detail ?: "Сервер повернув некоректні дані")

    class Unknown(val cause: Throwable?) : AppError("Сталася непередбачена помилка. Спробуйте ще раз")
}
