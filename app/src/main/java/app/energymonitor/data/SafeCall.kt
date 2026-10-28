package app.energymonitor.data

import kotlin.coroutines.cancellation.CancellationException

/**
 * Перетворює виняток на Result.failure, щоб ViewModel обробляла помилку як
 * звичайне значення. CancellationException обов'язково пробрасується далі:
 * якщо його "проковтнути", скасована корутина (наприклад, при виході з екрана)
 * продовжить виконуватися.
 */
inline fun <T> safeCall(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
