package app.energymonitor.data.source

import app.energymonitor.data.network.ApiExecutor
import app.energymonitor.data.network.InvalidResponseException
import app.energymonitor.data.remote.ReadingsApiService
import app.energymonitor.data.remote.dto.toDomainOrNull
import app.energymonitor.data.remote.dto.toDto
import app.energymonitor.domain.ReadingRecord

/**
 * Реалізація віддаленого джерела на Retrofit. Репозиторій, ViewModel та екрани
 * про неї нічого не знають: вони працюють з інтерфейсом ReadingsRemoteSource
 * і не залежать від деталей роботи Retrofit чи HTTP.
 */
class RetrofitReadingsRemoteSource(
    private val api: ReadingsApiService,
    private val executor: ApiExecutor
) : ReadingsRemoteSource {

    override suspend fun fetchAll(): List<ReadingRecord> =
        executor.execute("GET /readings") {
            // Некоректні записи відкидаються, решта даних лишається придатною.
            api.getReadings().mapNotNull { it.toDomainOrNull() }
        }

    override suspend fun add(record: ReadingRecord): ReadingRecord =
        executor.execute("POST /readings") {
            api.addReading(record.toDto()).toDomainOrNull() ?: throw invalid("POST /readings")
        }

    override suspend fun update(record: ReadingRecord): ReadingRecord =
        executor.execute("PUT /readings/${record.id}") {
            api.updateReading(record.id, record.toDto()).toDomainOrNull()
                ?: throw invalid("PUT /readings/${record.id}")
        }

    override suspend fun delete(id: Long) =
        executor.execute("DELETE /readings/$id") { api.deleteReading(id) }

    private fun invalid(operation: String) =
        InvalidResponseException("Сервер повернув некоректні дані ($operation)")
}
