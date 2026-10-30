package app.energymonitor

import app.energymonitor.data.remote.FailureMode
import app.energymonitor.data.remote.MockAuth
import app.energymonitor.data.remote.MockBackend
import app.energymonitor.data.remote.MockServerInterceptor
import app.energymonitor.data.remote.dto.MetersResponse
import app.energymonitor.data.remote.dto.ReadingDto
import app.energymonitor.data.remote.dto.toDomainOrNull
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** Перевіряє мок-сервер так, як його бачить застосунок: через справжній HTTP-клієнт. */
class MockServerTest {

    private val backend = MockBackend().apply { latencyMillis = 0 }
    private val auth = MockAuth()
    private val client = OkHttpClient.Builder().addInterceptor(MockServerInterceptor(backend, auth)).build()
    private val gson = Gson()
    private val base = "https://mock.energymonitor.local"
    private val adminToken = checkNotNull(auth.login("admin", "admin123"))

    private fun get(path: String, token: String? = adminToken): Response {
        val builder = Request.Builder().url(base + path).get()
        if (token != null) builder.header("Authorization", "Bearer $token")
        return client.newCall(builder.build()).execute()
    }

    private fun send(method: String, path: String, json: String, token: String? = adminToken): Response {
        val body = json.toRequestBody("application/json".toMediaType())
        val builder = Request.Builder().url(base + path)
        if (method == "POST") builder.post(body) else builder.put(body)
        if (token != null) builder.header("Authorization", "Bearer $token")
        return client.newCall(builder.build()).execute()
    }

    private fun newReading(label: String, voltage: Double = 230.0) =
        gson.toJson(ReadingDto(null, label, 1_000L, voltage, 10.0, 0.95))

    @Test
    fun `GET readings повертає JSON із полями за контрактом API`() {
        val response = get("/readings")
        val text = response.body!!.string()
        val items = gson.fromJson(text, Array<ReadingDto>::class.java)

        assertEquals(200, response.code)
        assertEquals(5, items.size)
        assertTrue(text.contains("\"power_factor\""))
        assertNotNull(items.first().toDomainOrNull())
    }

    @Test
    fun `POST створює запис, дублікат назви дає 409, некоректні дані 422`() {
        assertEquals(201, send("POST", "/readings", newReading("Нова точка")).code)
        assertEquals(6, gson.fromJson(get("/readings").body!!.string(), Array<ReadingDto>::class.java).size)

        assertEquals(409, send("POST", "/readings", newReading("нова ТОЧКА")).code)
        assertEquals(422, send("POST", "/readings", newReading("Інша", voltage = 999.0)).code)
    }

    @Test
    fun `PUT і DELETE для неіснуючого запису дають 404, для існуючого успіх`() {
        assertEquals(404, send("PUT", "/readings/9999", newReading("X")).code)

        val delete = { id: Long ->
            client.newCall(
                Request.Builder().url("$base/readings/$id").delete()
                    .header("Authorization", "Bearer $adminToken").build()
            ).execute()
        }
        assertEquals(200, delete(1).code)
        assertEquals(404, delete(1).code)
    }

    @Test
    fun `невідомий маршрут дає 404`() {
        assertEquals(404, get("/unknown").code)
    }

    @Test
    fun `вкладений JSON точок обліку розбирається в доменні моделі`() {
        val response = gson.fromJson(get("/meters").body!!.string(), MetersResponse::class.java)
        val meters = response.meters.orEmpty().mapNotNull { it.toDomainOrNull() }

        assertEquals(5, meters.size)
        assertEquals("Корпус 20", meters.first { it.name == "Вентиляція" }.building)
        assertEquals(3, meters.first { it.name == "Вентиляція" }.floor)
    }

    @Test
    fun `режими збоїв імітують помилки мережі та сервера`() {
        backend.failureMode = FailureMode.OFFLINE
        try {
            get("/readings"); fail("очікувався виняток")
        } catch (e: UnknownHostException) {
            assertTrue(e is IOException)
        }

        backend.failureMode = FailureMode.TIMEOUT
        try {
            get("/readings"); fail("очікувався виняток")
        } catch (e: SocketTimeoutException) {
            assertTrue(e is IOException)
        }

        backend.failureMode = FailureMode.SERVER_ERROR
        assertEquals(500, get("/readings").code)
    }

    @Test
    fun `нестабільний режим двічі поспіль збоїть, а третій запит проходить`() {
        backend.failureMode = FailureMode.FLAKY
        assertEquals(503, get("/readings").code)
        assertEquals(503, get("/readings").code)
        assertEquals(200, get("/readings").code)
        assertEquals(503, get("/readings").code)
    }

    @Test
    fun `DTO без обов'язкових полів відкидається`() {
        assertNull(ReadingDto(1, null, 1, 230.0, 10.0, 0.9).toDomainOrNull())
        assertNull(ReadingDto(1, "A", 1, 230.0, 10.0, 1.5).toDomainOrNull())
        assertNotNull(ReadingDto(1, "A", 1, 230.0, 10.0, 0.9).toDomainOrNull())
    }

    @Test
    fun `без токена захищені маршрути дають 401, вхід і реєстрація публічні`() {
        assertEquals(401, get("/readings", token = null).code)
        assertEquals(401, get("/meters", token = null).code)
        assertEquals(401, get("/readings", token = "сміття.без.підпису").code)

        val login = send("POST", "/auth/login", """{"login":"admin","password":"admin123"}""", token = null)
        assertEquals(200, login.code)
        assertTrue(login.body!!.string().contains("access_token"))
    }

    @Test
    fun `невірний пароль дає 401, а нова реєстрація 201 і повторна 409`() {
        assertEquals(401, send("POST", "/auth/login", """{"login":"admin","password":"хибний"}""", token = null).code)

        val register = """{"name":"Нова","email":"new@kpi.ua","password":"abc12345"}"""
        assertEquals(201, send("POST", "/auth/register", register, token = null).code)
        assertEquals(409, send("POST", "/auth/register", register, token = null).code)
        assertEquals(200, send("POST", "/auth/login", """{"login":"new@kpi.ua","password":"abc12345"}""", token = null).code)
    }

    @Test
    fun `оператор читає дані, але зміна показників дає 403`() {
        val operator = checkNotNull(auth.login("operator", "oper123"))

        assertEquals(200, get("/readings", operator).code)
        assertEquals(403, send("POST", "/readings", newReading("Нова"), operator).code)
        assertEquals(403, send("PUT", "/readings/1", newReading("Щит №1"), operator).code)
    }
}
