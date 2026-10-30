package app.energymonitor

import app.energymonitor.data.auth.AuthApiService
import app.energymonitor.data.auth.AuthRepository
import app.energymonitor.data.auth.Base64Url
import app.energymonitor.data.auth.Jwt
import app.energymonitor.data.auth.LoginRequest
import app.energymonitor.data.auth.RegisterRequest
import app.energymonitor.data.auth.SessionEvent
import app.energymonitor.data.auth.SessionManager
import app.energymonitor.data.auth.TokenResponse
import app.energymonitor.data.auth.TokenStorage
import app.energymonitor.data.network.ApiExecutor
import app.energymonitor.data.network.InvalidResponseException
import app.energymonitor.data.network.RetryPolicy
import app.energymonitor.data.remote.MockAuth
import app.energymonitor.data.remote.TokenCheck
import app.energymonitor.domain.Permissions
import app.energymonitor.domain.UserRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

private class MemoryStorage(var token: String? = null) : TokenStorage {
    override fun read(): String? = token
    override fun save(token: String) { this.token = token }
    override fun clear() { token = null }
}

private class FakeAuthApi(private val onLogin: (LoginRequest) -> TokenResponse) : AuthApiService {
    override suspend fun login(request: LoginRequest): TokenResponse = onLogin(request)
    override suspend fun register(request: RegisterRequest): TokenResponse = onLogin(LoginRequest(request.email, request.password))
}

class JwtAuthTest {

    private var now = 1_700_000_000_000L
    private val auth = MockAuth(clock = { now })

    @Test
    fun `Base64url кодує й декодує довільні байти та кирилицю`() {
        for (size in 0..40) {
            val bytes = ByteArray(size) { (it * 37 + 11).toByte() }
            assertArrayEquals(bytes, Base64Url.decode(Base64Url.encode(bytes)))
        }
        val text = "Адміністратор"
        assertEquals(text, String(Base64Url.decode(Base64Url.encode(text.toByteArray())), Charsets.UTF_8))
        assertEquals("YQ", Base64Url.encode("a".toByteArray()))
        assertFalse(Base64Url.encode(ByteArray(8) { -1 }).contains("="))
    }

    @Test
    fun `виданий токен розбирається в claims із роллю та терміном дії`() {
        auth.tokenTtlSeconds = 120
        val token = checkNotNull(auth.login("admin", "admin123"))
        val claims = checkNotNull(Jwt.decode(token))

        assertEquals(3, token.split('.').size)
        assertEquals("admin", claims.subject)
        assertEquals(UserRole.ADMIN, claims.role)
        assertEquals(120, claims.expiresAtSeconds - claims.issuedAtSeconds)
        assertFalse(claims.isExpired(now))
        assertTrue(claims.isExpired(now + 120_000))
    }

    @Test
    fun `сервер розрізняє відсутній, недійсний, підроблений і прострочений токен`() {
        auth.tokenTtlSeconds = 60
        val token = checkNotNull(auth.login("operator", "oper123"))

        assertEquals(TokenCheck.Missing, auth.verify(null))
        assertEquals(TokenCheck.Invalid, auth.verify("Bearer abc"))
        assertTrue(auth.verify("Bearer $token") is TokenCheck.Valid)

        // підміна ролі в payload ламає підпис
        val parts = token.split('.')
        val forgedPayload = Base64Url.encode(
            String(Base64Url.decode(parts[1]), Charsets.UTF_8).replace("USER", "ADMIN").toByteArray()
        )
        assertEquals(TokenCheck.Invalid, auth.verify("Bearer ${parts[0]}.$forgedPayload.${parts[2]}"))

        now += 61_000
        assertEquals(TokenCheck.Expired, auth.verify("Bearer $token"))
    }

    @Test
    fun `права визначаються роллю`() {
        assertTrue(Permissions.canEditReadings(UserRole.ADMIN))
        assertFalse(Permissions.canEditReadings(UserRole.USER))
        assertFalse(Permissions.canEditReadings(null))
        assertNull(UserRole.fromClaim("hacker"))
    }

    @Test
    fun `SessionManager стартує сесію, відновлює її після перезапуску і завершує прострочену`() {
        val storage = MemoryStorage()
        val token = checkNotNull(auth.login("admin", "admin123"))

        val manager = SessionManager(storage, clock = { now })
        assertNull(manager.session.value)
        val session = checkNotNull(manager.start(token))
        assertEquals(UserRole.ADMIN, session.role)
        assertEquals(token, storage.token)

        // "перезапуск": нова сесія зчитує токен зі сховища
        assertEquals("admin", SessionManager(storage, clock = { now }).session.value?.login)

        // токен прострочений — відновлення повертає порожню сесію
        now += 301_000
        assertNull(SessionManager(storage, clock = { now }).session.value)
        assertNull(manager.start("не.токен.зовсім"))
    }

    @Test
    fun `expire очищає сховище та повідомляє підписників`() = runBlocking {
        val storage = MemoryStorage()
        val manager = SessionManager(storage, clock = { now })
        manager.start(checkNotNull(auth.login("admin", "admin123")))

        val received = mutableListOf<SessionEvent>()
        val job = launch(Dispatchers.Unconfined) { manager.events.collect { received.add(it) } }
        manager.expire("Сесію завершено")
        job.cancel()

        assertNull(manager.session.value)
        assertNull(storage.token)
        assertEquals(1, received.size)
        assertEquals("Сесію завершено", (received.single() as SessionEvent.Expired).message)
    }

    @Test
    fun `AuthRepository починає сесію за відповіддю сервера й повертає помилки як Result`() = runBlocking {
        val executor = ApiExecutor(RetryPolicy(maxAttempts = 1))
        val manager = SessionManager(MemoryStorage(), clock = { now })

        val ok = AuthRepository(FakeAuthApi { req ->
            TokenResponse(auth.login(req.login.orEmpty(), req.password.orEmpty()), "Bearer", 300)
        }, executor, manager)
        assertTrue(ok.login("operator", "oper123").isSuccess)
        assertEquals(UserRole.USER, manager.session.value?.role)

        val rejected = AuthRepository(FakeAuthApi {
            throw HttpException(Response.error<Any>(401, "".toResponseBody(null)))
        }, executor, SessionManager(MemoryStorage(), clock = { now }))
        val failure = rejected.login("admin", "bad").exceptionOrNull()
        assertTrue(failure is HttpException && failure.code() == 401)

        val broken = AuthRepository(FakeAuthApi { TokenResponse(null, null, null) }, executor,
            SessionManager(MemoryStorage(), clock = { now }))
        assertTrue(broken.login("admin", "admin123").exceptionOrNull() is InvalidResponseException)
        assertNotNull(manager.session.value)
    }
}
