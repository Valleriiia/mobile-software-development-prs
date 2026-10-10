package app.energymonitor.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import app.energymonitor.domain.ConsumptionPoint
import app.energymonitor.domain.ConsumptionSimulator
import app.energymonitor.domain.EnergyCalculator
import app.energymonitor.domain.ReadingRecord

/**
 * Агрегує два джерела даних: постійну історію показників у Room (реальні
 * дані користувача) і типовий добовий профіль із ConsumptionSimulator, який
 * підключається лише як резерв, поки власної історії ще замало для прогнозу.
 */
class ReadingsRepository(private val dao: ReadingDao) {

    fun observeAll(): Flow<List<ReadingRecord>> =
        dao.observeAll().map { entities -> entities.map { it.toRecord() } }

    suspend fun insert(record: ReadingRecord) = dao.insert(record.toEntity())

    suspend fun delete(id: Long) = dao.deleteById(id)

    suspend fun forecastKw(): Double {
        val history = observeAll().first()
        if (history.size >= 3) {
            val recent = history.sortedByDescending { it.timestampMillis }
                .take(3)
                .map { ConsumptionPoint(hour = 0, powerKw = it.activePowerKw) }
            return EnergyCalculator.forecastNextHourKw(recent)
        }
        return EnergyCalculator.forecastNextHourKw(ConsumptionSimulator.dailyProfile())
    }
}
