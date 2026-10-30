package app.energymonitor.data

import android.content.Context
import androidx.room.Room
import app.energymonitor.data.auth.AuthApiService
import app.energymonitor.data.auth.AuthInterceptor
import app.energymonitor.data.auth.AuthRepository
import app.energymonitor.data.auth.SessionManager
import app.energymonitor.data.auth.SharedPreferencesTokenStorage
import app.energymonitor.data.network.ApiExecutor
import app.energymonitor.data.remote.MockAuth
import app.energymonitor.data.remote.MockBackend
import app.energymonitor.data.remote.MockServerInterceptor
import app.energymonitor.data.remote.ReadingsApiService
import app.energymonitor.data.remote.RetrofitClient
import app.energymonitor.data.source.RetrofitReadingsRemoteSource
import app.energymonitor.data.source.RoomReadingsLocalSource

/**
 * Ручне впровадження залежностей: єдине місце, де збираються база, мережа та
 * репозиторії. Контейнер живе стільки, скільки процес застосунку, тому поворот
 * екрана не створює другу базу й не "скидає" мок-сервер.
 */
class AppContainer private constructor(context: Context) {

    val database: AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "energy_monitor.db").build()

    val backend = MockBackend()
    val mockAuth = MockAuth()

    val sessionManager = SessionManager(SharedPreferencesTokenStorage(context))

    private val httpClient = RetrofitClient.buildHttpClient(
        AuthInterceptor(sessionManager),
        MockServerInterceptor(backend, mockAuth)
    )
    private val api: ReadingsApiService = RetrofitClient.createService(httpClient, ReadingsApiService::class.java)
    private val authApi: AuthApiService = RetrofitClient.createService(httpClient, AuthApiService::class.java)
    private val apiExecutor = ApiExecutor()

    val readingsRepository = ReadingsRepository(
        local = RoomReadingsLocalSource(database.readingDao(), database),
        remote = RetrofitReadingsRemoteSource(api, apiExecutor)
    )

    val metersRepository = MetersRepository(api, apiExecutor)

    val authRepository = AuthRepository(authApi, apiExecutor, sessionManager)

    companion object {
        @Volatile
        private var instance: AppContainer? = null

        fun get(context: Context): AppContainer =
            instance ?: synchronized(this) {
                instance ?: AppContainer(context.applicationContext).also { instance = it }
            }
    }
}
