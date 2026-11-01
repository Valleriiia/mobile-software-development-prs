package app.energymonitor.data.error

import app.energymonitor.data.network.InvalidResponseException
import app.energymonitor.data.network.NoNetworkException
import app.energymonitor.data.remote.dto.ApiErrorDto
import com.google.gson.Gson
import com.google.gson.JsonParseException
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * Єдине місце, яке перетворює будь-який виняток на AppError. ViewModel не
 * аналізує HTTP-коди сама: вона просить ErrorHandler і показує готовий текст.
 */
object ErrorHandler {

    private val gson = Gson()

    fun map(error: Throwable): AppError = when (error) {
        is HttpException -> fromHttp(error.code(), serverMessage(error))
        is NoNetworkException -> AppError.NoNetwork
        // Таймаут — теж IOException, тому розглядається раніше за загальний IOException.
        is SocketTimeoutException -> AppError.Timeout
        is IOException -> AppError.NoNetwork
        is InvalidResponseException -> AppError.InvalidData(error.message)
        else -> AppError.Unknown(error)
    }

    fun userMessage(error: Throwable): String = map(error).message

    fun fromHttp(code: Int, serverMessage: String?): AppError = when (code) {
        400, 422 -> AppError.Validation(serverMessage)
        401 -> AppError.Unauthorized
        403 -> AppError.Forbidden
        404 -> AppError.NotFound
        408 -> AppError.Timeout
        409 -> AppError.Conflict(serverMessage)
        429 -> AppError.TooManyRequests
        in 500..599 -> AppError.Server(code)
        else -> AppError.Unknown(null)
    }

    /** Пояснення з тіла відповіді {"error": "...", "message": "..."}; null, якщо тіла немає. */
    private fun serverMessage(error: HttpException): String? = try {
        val body = error.response()?.errorBody()?.string()
        if (body.isNullOrBlank()) null else gson.fromJson(body, ApiErrorDto::class.java)?.message
    } catch (e: JsonParseException) {
        null
    } catch (e: IOException) {
        null
    }
}
