package app.energymonitor

import app.energymonitor.data.network.RetryPolicy
import app.energymonitor.data.network.isTransientError
import app.energymonitor.data.network.withRetry
import app.energymonitor.data.remote.MockBackend
import app.energymonitor.data.remote.dto.toDomainOrNull
import app.energymonitor.data.source.DemoData
import app.energymonitor.domain.LoadLevel
import app.energymonitor.domain.MeterAnalytics
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

class RetryAndAnalyticsTest {

    private val fast = RetryPolicy(maxAttempts = 3, initialDelayMillis = 1, backoffFactor = 2.0)

    private fun http(code: Int) = HttpException(Response.error<Any>(code, "".toResponseBody(null)))

    @Test
    fun `затримка між спробами зростає експоненційно`() {
        val policy = RetryPolicy()
        assertEquals(400, policy.delayAfterAttempt(1))
        assertEquals(800, policy.delayAfterAttempt(2))
        assertEquals(1600, policy.delayAfterAttempt(3))
    }

    @Test
    fun `тимчасові збої повторюються, постійні помилки ні`() {
        assertTrue(isTransientError(IOException()))
        assertTrue(isTransientError(http(503)))
        assertTrue(isTransientError(http(429)))
        assertFalse(isTransientError(http(404)))
        assertFalse(isTransientError(http(401)))
        assertFalse(isTransientError(IllegalStateException()))
    }

    @Test
    fun `запит успішний з третьої спроби`() = runBlocking {
        var calls = 0
        val result = withRetry(fast) {
            calls++
            if (calls < 3) throw IOException("збій $calls")
            "ok"
        }
        assertEquals("ok", result)
        assertEquals(3, calls)
    }

    @Test
    fun `помилка 404 не повторюється`() = runBlocking {
        var calls = 0
        try {
            withRetry(fast) { calls++; throw http(404) }
            fail("очікувався виняток")
        } catch (e: HttpException) {
            assertEquals(404, e.code())
        }
        assertEquals(1, calls)
    }

    @Test
    fun `після вичерпання спроб виняток пробрасується`() = runBlocking {
        var calls = 0
        val retries = mutableListOf<Int>()
        try {
            withRetry(fast, onRetry = { attempt, _ -> retries.add(attempt) }) { calls++; throw IOException("down") }
            fail("очікувався виняток")
        } catch (e: IOException) {
            assertEquals("down", e.message)
        }
        assertEquals(3, calls)
        assertEquals(listOf(1, 2), retries)
    }

    @Test
    fun `картки точок обліку об'єднують довідник, показники й тарифи`() {
        val backend = MockBackend()
        val meters = backend.meters.mapNotNull { it.toDomainOrNull() }
        val tariffs = backend.tariffs.mapNotNull { it.toDomainOrNull() }
        val cards = MeterAnalytics.buildCards(meters, DemoData.readings(), tariffs)
        val byName = cards.associateBy { it.meter.name }

        // Щит №1: 230 В * 12 А * 0.95 = 2.622 кВт при договірних 6 кВт
        assertEquals(2.622, byName.getValue("Щит №1").powerKw!!, 0.0001)
        assertEquals(43.7, byName.getValue("Щит №1").loadPercent!!, 0.05)
        assertEquals(LoadLevel.NORMAL, byName.getValue("Щит №1").level)
        // Вартість доби: 2.622 * (8*2.16 + 12*4.32 + 4*6.48) = 2.622 * 95.04
        assertEquals(249.19, byName.getValue("Щит №1").dailyCostUah!!, 0.01)
        assertEquals(LoadLevel.WARNING, byName.getValue("Серверна").level)
        assertEquals(LoadLevel.OVERLOAD, byName.getValue("Вентиляція").level)
        assertEquals(105.0, byName.getValue("Вентиляція").loadPercent!!, 0.05)
    }

    @Test
    fun `зведення по всіх точках і точка без показника`() {
        val backend = MockBackend()
        val meters = backend.meters.mapNotNull { it.toDomainOrNull() }
        val tariffs = backend.tariffs.mapNotNull { it.toDomainOrNull() }

        val all = MeterAnalytics.summarize(MeterAnalytics.buildCards(meters, DemoData.readings(), tariffs))
        assertEquals(5, all.meterCount)
        assertEquals(24.0, all.totalContractKw, 0.0001)
        assertEquals(16.5477, all.totalPowerKw, 0.001)
        assertEquals(68.95, all.utilizationPercent, 0.01)
        assertEquals(1, all.overloadedCount)

        val partial = MeterAnalytics.buildCards(meters, DemoData.readings().drop(1), tariffs)
        assertNull(partial.first { it.meter.name == "Щит №1" }.powerKw)
        assertEquals(1, MeterAnalytics.summarize(partial).withoutDataCount)
        assertNotNull(partial.first { it.meter.name == "Щит №2" }.powerKw)
    }
}
