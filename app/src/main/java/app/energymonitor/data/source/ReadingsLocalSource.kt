package app.energymonitor.data.source

import androidx.room.RoomDatabase
import androidx.room.withTransaction
import app.energymonitor.data.ReadingDao
import app.energymonitor.data.toEntity
import app.energymonitor.data.toRecord
import app.energymonitor.domain.ReadingRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Локальне джерело даних. Repository залежить від цього інтерфейсу, а не від
 * Room напряму, тому в unit-тестах його легко підмінити простою реалізацією
 * в пам'яті, а саму базу — замінити, не чіпаючи бізнес-логіку.
 */
interface ReadingsLocalSource {
    fun observeAll(): Flow<List<ReadingRecord>>
    suspend fun count(): Int
    suspend fun upsert(record: ReadingRecord)
    suspend fun update(record: ReadingRecord)
    suspend fun delete(id: Long)

    /** Повністю замінює вміст кешу даними з сервера в одній транзакції. */
    suspend fun replaceAll(records: List<ReadingRecord>)
}

/** Реалізація на Room: єдиний клас, який знає про DAO та сутності бази. */
class RoomReadingsLocalSource(
    private val dao: ReadingDao,
    private val database: RoomDatabase
) : ReadingsLocalSource {

    override fun observeAll(): Flow<List<ReadingRecord>> =
        dao.observeAll().map { entities -> entities.map { it.toRecord() } }

    override suspend fun count(): Int = dao.count()

    override suspend fun upsert(record: ReadingRecord) = dao.upsert(record.toEntity())

    override suspend fun update(record: ReadingRecord) = dao.update(record.toEntity())

    override suspend fun delete(id: Long) = dao.deleteById(id)

    // Транзакція гарантує, що UI ніколи не побачить проміжний стан
    // "таблицю вже очищено, нові дані ще не вставлені".
    override suspend fun replaceAll(records: List<ReadingRecord>) {
        database.withTransaction {
            dao.clear()
            dao.upsertAll(records.map { it.toEntity() })
        }
    }
}
