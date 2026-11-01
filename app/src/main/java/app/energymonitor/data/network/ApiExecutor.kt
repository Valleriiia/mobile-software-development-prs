package app.energymonitor.data.network

import app.energymonitor.data.error.ErrorLogger
import app.energymonitor.data.error.LogLevel
import app.energymonitor.data.error.NoOpErrorLogger
import retrofit2.HttpException
import kotlin.coroutines.cancellation.CancellationException

/**
 * Єдина точка виконання мережевих запитів: перевірка мережі, повтори з паузою
 * та журналювання. Усі джерела даних викликають API лише через неї, тому
 * поведінка при збоях змінюється в одному місці. Остаточну помилку виконавець
 * пробрасує без змін, а перетворення її на повідомлення виконує ErrorHandler.
 */
class ApiExecutor(
    private val retryPolicy: RetryPolicy = RetryPolicy(),
    private val networkMonitor: NetworkMonitor = AlwaysOnline,
    private val logger: ErrorLogger = NoOpErrorLogger
) {

    suspend fun <T> execute(operation: String, block: suspend () -> T): T {
        // Перевірка йде до повторів: без мережі повторювати марно.
        if (!networkMonitor.isOnline()) {
            logger.log(LogLevel.WARN, "$operation: немає підключення до мережі, запит не надсилався")
            throw NoNetworkException()
        }
        try {
            return withRetry(
                policy = retryPolicy,
                onRetry = { attempt, error ->
                    logger.log(LogLevel.WARN, "$operation: спроба $attempt не вдалася (${describe(error)}), повтор")
                },
                block = block
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.log(LogLevel.ERROR, "$operation: запит не вдався (${describe(e)})")
            throw e
        }
    }

    private fun describe(error: Throwable): String =
        if (error is HttpException) "HTTP ${error.code()}" else "${error::class.java.simpleName}: ${error.message}"
}

/** Сервер відповів 2xx, але тіло не відповідає очікуваному контракту. */
class InvalidResponseException(message: String) : RuntimeException(message)
