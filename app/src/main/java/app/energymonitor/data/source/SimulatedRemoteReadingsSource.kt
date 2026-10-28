package app.energymonitor.data.source

import app.energymonitor.domain.ReadingRecord
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Імітація віддаленого сервера в пам'яті зі штучною затримкою мережі
 * для відпрацювання асинхронної взаємодії з Repository. */
class SimulatedRemoteReadingsSource(
    seed: List<ReadingRecord> = DemoData.readings(),
    private val latencyMillis: Long = 500
) : ReadingsRemoteSource {

    private val storage = seed.toMutableList()

    // Кілька корутин можуть звертатися до "сервера" одночасно.
    private val mutex = Mutex()

    override suspend fun fetchAll(): List<ReadingRecord> {
        delay(latencyMillis)
        return mutex.withLock { storage.toList() }
    }

    override suspend fun add(record: ReadingRecord): ReadingRecord {
        delay(latencyMillis)
        return mutex.withLock {
            storage.add(record)
            record
        }
    }

    override suspend fun update(record: ReadingRecord): ReadingRecord {
        delay(latencyMillis)
        return mutex.withLock {
            val index = storage.indexOfFirst { it.id == record.id }
            if (index < 0) throw NoSuchElementException("Показник ${record.id} не знайдено на сервері")
            storage[index] = record
            record
        }
    }

    override suspend fun delete(id: Long) {
        delay(latencyMillis)
        mutex.withLock { storage.removeAll { it.id == id } }
    }
}
