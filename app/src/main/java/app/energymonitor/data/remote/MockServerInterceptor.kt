package app.energymonitor.data.remote

import app.energymonitor.data.remote.dto.ApiErrorDto
import app.energymonitor.data.remote.dto.ReadingDto
import com.google.gson.Gson
import com.google.gson.JsonParseException
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Термінальний interceptor: ніколи не викликає chain.proceed(), а сам формує
 * HTTP-відповідь на основі MockBackend. Для Retrofit це виглядає як звичайна
 * мережа. У реальному проєкті цей interceptor просто не додається, а в
 * RetrofitClient вказується справжня адреса сервера.
 */
class MockServerInterceptor(
    private val backend: MockBackend,
    private val gson: Gson = Gson()
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        simulateLatency()
        failureResponse(request)?.let { return it }
        return route(request)
    }

    private fun simulateLatency() {
        try {
            Thread.sleep(backend.latencyMillis)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException("Запит перервано")
        }
    }

    // Збої мережевого рівня імітуються винятками (як у справжньої мережі),
    // збої сервера — HTTP-відповідями з кодами 5xx.
    private fun failureResponse(request: Request): Response? = when (backend.failureMode) {
        FailureMode.NONE -> null
        FailureMode.OFFLINE -> throw UnknownHostException("Unable to resolve host \"mock.energymonitor.local\"")
        FailureMode.TIMEOUT -> throw SocketTimeoutException("timeout")
        FailureMode.SERVER_ERROR -> error(request, 500, "server_error", "Внутрішня помилка сервера")
        FailureMode.FLAKY ->
            if (backend.shouldFailFlaky()) error(request, 503, "unavailable", "Сервіс тимчасово недоступний") else null
    }

    private fun route(request: Request): Response {
        val path = request.url.encodedPath
        val method = request.method
        return when {
            method == "GET" && path == "/readings" -> ok(request, backend.listReadings())
            method == "POST" && path == "/readings" -> createReading(request)
            path.startsWith("/readings/") -> readingById(request, method, path.removePrefix("/readings/"))
            method == "GET" && path == "/meters" -> ok(request, backend.metersResponse())
            method == "GET" && path == "/tariffs" -> ok(request, backend.tariffs)
            else -> error(request, 404, "not_found", "Маршрут $method $path не знайдено")
        }
    }

    private fun createReading(request: Request): Response {
        val dto = readBody<ReadingDto>(request)
            ?: return error(request, 400, "bad_request", "Некоректне тіло запиту")
        backend.validate(dto)?.let { return error(request, 422, "validation_failed", it) }
        if (backend.labelTaken(dto.label.orEmpty(), exceptId = null)) {
            return error(request, 409, "conflict", "Точка обліку з такою назвою вже існує")
        }
        return json(request, 201, "Created", gson.toJson(backend.addReading(dto)))
    }

    private fun readingById(request: Request, method: String, idText: String): Response {
        val id = idText.toLongOrNull() ?: return error(request, 400, "bad_request", "Некоректний id")
        return when (method) {
            "PUT" -> {
                val dto = readBody<ReadingDto>(request)
                    ?: return error(request, 400, "bad_request", "Некоректне тіло запиту")
                backend.validate(dto)?.let { return error(request, 422, "validation_failed", it) }
                if (backend.labelTaken(dto.label.orEmpty(), exceptId = id)) {
                    return error(request, 409, "conflict", "Точка обліку з такою назвою вже існує")
                }
                val saved = backend.updateReading(id, dto)
                    ?: return error(request, 404, "not_found", "Показник $id не знайдено")
                ok(request, saved)
            }
            "DELETE" ->
                if (backend.deleteReading(id)) json(request, 200, "OK", """{"deleted":true}""")
                else error(request, 404, "not_found", "Показник $id не знайдено")
            else -> error(request, 405, "method_not_allowed", "Метод $method не підтримується")
        }
    }

    private inline fun <reified T> readBody(request: Request): T? {
        val buffer = Buffer()
        request.body?.writeTo(buffer)
        return try {
            gson.fromJson(buffer.readUtf8(), T::class.java)
        } catch (e: JsonParseException) {
            null
        }
    }

    private fun ok(request: Request, body: Any): Response = json(request, 200, "OK", gson.toJson(body))

    private fun error(request: Request, code: Int, error: String, message: String): Response =
        json(request, code, error, gson.toJson(ApiErrorDto(error, message)))

    private fun json(request: Request, code: Int, message: String, body: String): Response =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(message)
            .body(body.toResponseBody("application/json; charset=utf-8".toMediaType()))
            .build()
}
