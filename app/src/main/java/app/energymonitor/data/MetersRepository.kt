package app.energymonitor.data

import app.energymonitor.data.network.ApiExecutor
import app.energymonitor.data.remote.ReadingsApiService
import app.energymonitor.data.remote.dto.toDomainOrNull
import app.energymonitor.domain.Meter
import app.energymonitor.domain.Tariff
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** Довідник точок обліку та тарифи; staleReason заповнений, якщо дані з кешу, бо сервер недоступний. */
data class MetersSnapshot(
    val meters: List<Meter>,
    val tariffs: List<Tariff>,
    val staleReason: Throwable? = null
) {
    val isStale: Boolean get() = staleReason != null
}

/**
 * Довідкові дані (точки обліку й тарифи) змінюються рідко, тому зберігаються
 * лише в пам'яті з TTL. Якщо після невдалого запиту кеш уже є, користувач
 * бачить останні відомі дані з позначкою "застарілі", а не порожній екран.
 */
class MetersRepository(
    private val api: ReadingsApiService,
    private val executor: ApiExecutor,
    private val cacheTtlMillis: Long = 5 * 60 * 1000L,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private var cache: MetersSnapshot? = null
    private var loadedAt = 0L

    suspend fun load(force: Boolean = false): Result<MetersSnapshot> {
        val cached = cache
        if (!force && cached != null && clock() - loadedAt < cacheTtlMillis) {
            return Result.success(cached)
        }
        return safeCall { fetch() }.fold(
            onSuccess = { fresh ->
                cache = fresh
                loadedAt = clock()
                Result.success(fresh)
            },
            onFailure = { error ->
                if (cached != null) Result.success(cached.copy(staleReason = error))
                else Result.failure(error)
            }
        )
    }

    // Два незалежні ендпоінти опитуються паралельно: загальний час = найповільніший із них.
    private suspend fun fetch(): MetersSnapshot = coroutineScope {
        val metersCall = async { executor.execute("GET /meters") { api.getMeters() } }
        val tariffsCall = async { executor.execute("GET /tariffs") { api.getTariffs() } }
        MetersSnapshot(
            meters = metersCall.await().meters.orEmpty().mapNotNull { it.toDomainOrNull() },
            tariffs = tariffsCall.await().mapNotNull { it.toDomainOrNull() }
        )
    }
}
