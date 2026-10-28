package app.energymonitor.data

import app.energymonitor.data.source.ReadingsLocalSource
import app.energymonitor.data.source.ReadingsRemoteSource
import app.energymonitor.domain.ConsumptionPoint
import app.energymonitor.domain.ConsumptionSimulator
import app.energymonitor.domain.EnergyCalculator
import app.energymonitor.domain.ReadingRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/** Результат синхронізації: дані взято з актуального кешу чи завантажено з сервера. */
enum class RefreshOutcome { CACHE_FRESH, UPDATED }

/**
 * Єдина точка доступу до показників. ViewModel не знає, скільки існує джерел
 * і звідки беруться дані.
 *
 * Архітектурні рішення:
 *  - локальна база (Room) є єдиним джерелом правди для UI: екран завжди читає
 *    Flow із кешу, а сервер лише наповнює кеш. Завдяки цьому застосунок
 *    працює офлайн і не має двох розбіжних списків;
 *  - кеш має термін життя (TTL): поки він не вичерпаний, зайвих запитів
 *    до сервера немає;
 *  - операції запису спершу виконуються на сервері і лише після успіху
 *    потрапляють у кеш, тож кеш ніколи не "обганяє" сервер;
 *  - помилки не кидаються назовні, а повертаються як Result — рішення про
 *    повідомлення користувачу ухвалює ViewModel.
 */
class ReadingsRepository(
    private val local: ReadingsLocalSource,
    private val remote: ReadingsRemoteSource,
    private val cacheTtlMillis: Long = DEFAULT_CACHE_TTL_MILLIS,
    private val clock: () -> Long = System::currentTimeMillis
) {

    private var lastRefreshMillis = 0L

    fun observeAll(): Flow<List<ReadingRecord>> = local.observeAll()

    /** Поточний знімок кешу — для перевірок, яким не потрібна підписка на Flow. */
    suspend fun currentRecords(): List<ReadingRecord> = local.observeAll().first()

    /**
     * Оновлює кеш із сервера. За невдачі кеш не чіпається, тож користувач
     * і далі бачить останні відомі дані (офлайн-режим).
     */
    suspend fun refresh(force: Boolean = false): Result<RefreshOutcome> {
        val now = clock()
        val cacheIsFresh = now - lastRefreshMillis < cacheTtlMillis
        if (!force && cacheIsFresh && local.count() > 0) {
            return Result.success(RefreshOutcome.CACHE_FRESH)
        }
        return safeCall {
            local.replaceAll(remote.fetchAll())
            lastRefreshMillis = now
            RefreshOutcome.UPDATED
        }
    }

    /** Прямий запит до сервера без запису в кеш — режим "Віддалено". */
    suspend fun fetchRemoteSnapshot(): Result<List<ReadingRecord>> = safeCall { remote.fetchAll() }

    suspend fun add(record: ReadingRecord): Result<ReadingRecord> = safeCall {
        val saved = remote.add(record)
        local.upsert(saved)
        saved
    }

    suspend fun update(record: ReadingRecord): Result<ReadingRecord> = safeCall {
        val saved = remote.update(record)
        local.update(saved)
        saved
    }

    suspend fun delete(id: Long): Result<Unit> = safeCall {
        remote.delete(id)
        local.delete(id)
    }

    /**
     * Бізнес-логіка поверх даних: прогноз на наступну годину. Якщо власних
     * показників менше трьох, для прогнозу використовується типовий добовий
     * профіль об'єкта.
     */
    suspend fun forecastKw(): Double {
        val history = currentRecords()
        if (history.size >= HISTORY_FOR_FORECAST) {
            val recent = history.sortedByDescending { it.timestampMillis }
                .take(HISTORY_FOR_FORECAST)
                .map { ConsumptionPoint(hour = 0, powerKw = it.activePowerKw) }
            return EnergyCalculator.forecastNextHourKw(recent)
        }
        return EnergyCalculator.forecastNextHourKw(ConsumptionSimulator.dailyProfile())
    }

    companion object {
        const val DEFAULT_CACHE_TTL_MILLIS = 5 * 60 * 1000L
        private const val HISTORY_FOR_FORECAST = 3
    }
}
