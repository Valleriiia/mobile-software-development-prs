package app.energymonitor

import app.energymonitor.data.error.AppError
import app.energymonitor.data.error.CompositeErrorLogger
import app.energymonitor.data.error.ErrorHandler
import app.energymonitor.data.error.ErrorLogger
import app.energymonitor.data.error.FileErrorLogger
import app.energymonitor.data.error.LogLevel
import app.energymonitor.data.network.ApiExecutor
import app.energymonitor.data.network.InvalidResponseException
import app.energymonitor.data.network.NetworkMonitor
import app.energymonitor.data.network.NoNetworkException
import app.energymonitor.data.network.RetryPolicy
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

private class RecordingLogger : ErrorLogger {
    val entries = mutableListOf<Pair<LogLevel, String>>()
    override fun log(level: LogLevel, message: String) { entries.add(level to message) }
    override fun readAll(): List<String> = entries.map { it.second }.reversed()
    override fun clear() = entries.clear()
}

class ErrorHandlingTest {

    private fun http(code: Int, body: String = "") =
        HttpException(Response.error<Any>(code, body.toResponseBody(null)))

    @Test
    fun `HTTP-коди перетворюються на типи помилок`() {
        assertEquals(AppError.Unauthorized, ErrorHandler.map(http(401)))
        assertEquals(AppError.Forbidden, ErrorHandler.map(http(403)))
        assertEquals(AppError.NotFound, ErrorHandler.map(http(404)))
        assertEquals(AppError.Timeout, ErrorHandler.map(http(408)))
        assertEquals(AppError.TooManyRequests, ErrorHandler.map(http(429)))
        assertTrue(ErrorHandler.map(http(422)) is AppError.Validation)
        assertTrue(ErrorHandler.map(http(409)) is AppError.Conflict)
        assertEquals(503, (ErrorHandler.map(http(503)) as AppError.Server).code)
        assertTrue(ErrorHandler.userMessage(http(500)).contains("500"))
    }

    @Test
    fun `пояснення сервера з тіла відповіді потрапляє в повідомлення`() {
        val conflict = http(409, """{"error":"conflict","message":"Точка обліку з такою назвою вже існує"}""")
        assertEquals("Точка обліку з такою назвою вже існує", ErrorHandler.userMessage(conflict))

        // порожнє або зіпсоване тіло не ламає обробник
        assertEquals("Такі дані вже існують", ErrorHandler.userMessage(http(409)))
        assertEquals("Такі дані вже існують", ErrorHandler.userMessage(http(409, "<html>не json</html>")))
    }

    @Test
    fun `мережеві винятки розрізняються за типом`() {
        assertEquals(AppError.NoNetwork, ErrorHandler.map(UnknownHostException("host")))
        assertEquals(AppError.NoNetwork, ErrorHandler.map(NoNetworkException()))
        assertEquals(AppError.NoNetwork, ErrorHandler.map(IOException("reset")))
        assertEquals(AppError.Timeout, ErrorHandler.map(SocketTimeoutException("timeout")))
        assertTrue(ErrorHandler.map(InvalidResponseException("Некоректна відповідь")) is AppError.InvalidData)
        assertTrue(ErrorHandler.map(IllegalStateException("баг")) is AppError.Unknown)
    }

    @Test
    fun `журнал пише найновіше першим, прибирає переноси та очищується`() {
        val file = File.createTempFile("errors", ".log").apply { deleteOnExit() }
        var now = 1_700_000_000_000L
        val logger = FileErrorLogger(file, clock = { now })

        logger.log(LogLevel.WARN, "перший")
        now += 1000
        logger.log(LogLevel.ERROR, "другий\nз переносом")

        val lines = logger.readAll()
        assertEquals(2, lines.size)
        assertTrue(lines[0].contains("[ERROR] другий з переносом"))
        assertTrue(lines[1].contains("[WARN] перший"))

        logger.clear()
        assertTrue(logger.readAll().isEmpty())
    }

    @Test
    fun `журнал не росте безмежно`() {
        val file = File.createTempFile("errors", ".log").apply { deleteOnExit() }
        val logger = FileErrorLogger(file, maxBytes = 200, maxLines = 3)

        repeat(20) { logger.log(LogLevel.INFO, "запис номер $it") }

        val lines = logger.readAll()
        assertTrue(lines.size <= 4)
        assertTrue(lines.first().contains("запис номер 19"))
    }

    @Test
    fun `складений журнал пише в усі місця`() {
        val a = RecordingLogger()
        val b = RecordingLogger()
        CompositeErrorLogger(a, b).log(LogLevel.INFO, "x")
        assertEquals(1, a.entries.size)
        assertEquals(1, b.entries.size)
    }

    private val fast = RetryPolicy(maxAttempts = 3, initialDelayMillis = 1)

    @Test
    fun `без мережі запит не надсилається й не повторюється`() = runBlocking {
        val logger = RecordingLogger()
        val executor = ApiExecutor(fast, networkMonitor = object : NetworkMonitor {
            override fun isOnline() = false
        }, logger = logger)

        var calls = 0
        try {
            executor.execute("GET /readings") { calls++ }
            fail("очікувався виняток")
        } catch (e: NoNetworkException) {
            assertTrue(e is IOException)
        }

        assertEquals(0, calls)
        assertEquals(1, logger.entries.size)
        assertEquals(LogLevel.WARN, logger.entries.single().first)
    }

    @Test
    fun `повтори журналюються як попередження, остаточна невдача як помилка`() = runBlocking {
        val logger = RecordingLogger()
        val executor = ApiExecutor(fast, logger = logger)

        var calls = 0
        val result = executor.execute("GET /readings") {
            calls++
            if (calls < 3) throw http(503)
            "ok"
        }
        assertEquals("ok", result)
        assertEquals(2, logger.entries.count { it.first == LogLevel.WARN })
        assertEquals(0, logger.entries.count { it.first == LogLevel.ERROR })

        logger.clear()
        try {
            executor.execute("GET /meters") { throw IOException("down") }
            fail("очікувався виняток")
        } catch (e: IOException) {
            assertEquals("down", e.message)
        }
        assertEquals(2, logger.entries.count { it.first == LogLevel.WARN })
        assertEquals(1, logger.entries.count { it.first == LogLevel.ERROR })
        assertTrue(logger.entries.last().second.contains("GET /meters"))
    }

    @Test
    fun `помилка 404 не повторюється і журналюється один раз`() = runBlocking {
        val logger = RecordingLogger()
        val executor = ApiExecutor(fast, logger = logger)
        var calls = 0

        try {
            executor.execute("DELETE /readings/9") { calls++; throw http(404) }
            fail("очікувався виняток")
        } catch (e: HttpException) {
            assertEquals(404, e.code())
        }

        assertEquals(1, calls)
        assertEquals(1, logger.entries.size)
        assertFalse(logger.entries.any { it.first == LogLevel.WARN })
    }
}
