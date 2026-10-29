package app.energymonitor.data.network

import kotlinx.coroutines.delay
import retrofit2.HttpException
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/** Скільки разів повторювати запит і як зростає пауза між спробами. */
data class RetryPolicy(
    val maxAttempts: Int = 3,
    val initialDelayMillis: Long = 400,
    val backoffFactor: Double = 2.0
) {
    /** Пауза після невдалої спроби №attempt (нумерація з 1): 400, 800, 1600… мс. */
    fun delayAfterAttempt(attempt: Int): Long {
        var delayMillis = initialDelayMillis.toDouble()
        repeat(attempt - 1) { delayMillis *= backoffFactor }
        return delayMillis.toLong()
    }
}

private val TRANSIENT_HTTP_CODES = setOf(408, 429, 500, 502, 503, 504)

/**
 * Повторювати має сенс лише для тимчасових збоїв: обрив зв'язку, таймаут,
 * перевантаження чи помилка сервера. Помилки 400/401/403/404 від повтору
 * не зникнуть, тому їх повертаємо одразу.
 */
fun isTransientError(error: Throwable): Boolean = when (error) {
    is HttpException -> error.code() in TRANSIENT_HTTP_CODES
    is IOException -> true
    else -> false
}

/** Виконує block, повторюючи його за політикою policy (експоненційна затримка). */
suspend fun <T> withRetry(
    policy: RetryPolicy,
    isRetryable: (Throwable) -> Boolean = ::isTransientError,
    onRetry: (attempt: Int, error: Throwable) -> Unit = { _, _ -> },
    block: suspend () -> T
): T {
    var attempt = 1
    while (true) {
        try {
            return block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (attempt >= policy.maxAttempts || !isRetryable(e)) throw e
            onRetry(attempt, e)
            delay(policy.delayAfterAttempt(attempt))
            attempt++
        }
    }
}
