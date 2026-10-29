package app.energymonitor.data.network

/** Єдина точка виконання мережевих запитів. Інкапсулює політику
 * повторних спроб (retry), щоб не дублювати її в кожному джерелі даних. */
class ApiExecutor(private val retryPolicy: RetryPolicy = RetryPolicy()) {

    suspend fun <T> execute(operation: String, block: suspend () -> T): T =
        withRetry(policy = retryPolicy, block = block)
}

/** Сервер відповів 2xx, але тіло не відповідає очікуваному контракту. */
class InvalidResponseException(message: String) : RuntimeException(message)
