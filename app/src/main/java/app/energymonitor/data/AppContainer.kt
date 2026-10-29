package app.energymonitor.data

import android.content.Context
import androidx.room.Room
import app.energymonitor.data.network.ApiExecutor
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

    private val httpClient = RetrofitClient.buildHttpClient(MockServerInterceptor(backend))
    private val api: ReadingsApiService = RetrofitClient.createService(httpClient, ReadingsApiService::class.java)
    private val apiExecutor = ApiExecutor()

    val readingsRepository = ReadingsRepository(
        local = RoomReadingsLocalSource(database.readingDao(), database),
        remote = RetrofitReadingsRemoteSource(api, apiExecutor)
    )

    val metersRepository = MetersRepository(api, apiExecutor)

    companion object {
        @Volatile
        private var instance: AppContainer? = null

        fun get(context: Context): AppContainer =
            instance ?: synchronized(this) {
                instance ?: AppContainer(context.applicationContext).also { instance = it }
            }
    }
}
