package app.energymonitor

import app.energymonitor.data.ReadingsRepository
import app.energymonitor.data.RefreshOutcome
import app.energymonitor.data.source.ReadingsLocalSource
import app.energymonitor.data.source.ReadingsRemoteSource
import app.energymonitor.domain.ReadingRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** Локальне джерело в пам'яті — Repository не знає, що це не Room. */
private class FakeLocal : ReadingsLocalSource {
    val items = MutableStateFlow<List<ReadingRecord>>(emptyList())
    override fun observeAll(): Flow<List<ReadingRecord>> = items
    override suspend fun count(): Int = items.value.size
    override suspend fun upsert(record: ReadingRecord) {
        items.value = items.value.filter { it.id != record.id } + record
    }
    override suspend fun update(record: ReadingRecord) = upsert(record)
    override suspend fun delete(id: Long) {
        items.value = items.value.filter { it.id != id }
    }
    override suspend fun replaceAll(records: List<ReadingRecord>) {
        items.value = records
    }
}

private class FakeRemote(var data: List<ReadingRecord>, var failing: Boolean = false) : ReadingsRemoteSource {
    var fetchCalls = 0
    private fun check() {
        if (failing) throw IOException("Немає зв'язку")
    }
    override suspend fun fetchAll(): List<ReadingRecord> {
        fetchCalls++
        check()
        return data
    }
    override suspend fun add(record: ReadingRecord): ReadingRecord {
        check()
        data = data + record
        return record
    }
    override suspend fun update(record: ReadingRecord): ReadingRecord {
        check()
        data = data.map { if (it.id == record.id) record else it }
        return record
    }
    override suspend fun delete(id: Long) {
        check()
        data = data.filter { it.id != id }
    }
}

class ReadingsRepositoryTest {

    private fun record(id: Long, label: String = "Точка $id") =
        ReadingRecord(id, label, 1_000L * id, 230.0, 10.0, 0.95)

    @Test
    fun `refresh переносить дані сервера в локальний кеш`() = runBlocking {
        val remote = FakeRemote(listOf(record(1), record(2)))
        val local = FakeLocal()
        val repository = ReadingsRepository(local, remote)

        val result = repository.refresh()

        assertEquals(RefreshOutcome.UPDATED, result.getOrNull())
        assertEquals(2, local.items.value.size)
    }

    @Test
    fun `поки кеш свіжий, повторний refresh не звертається до сервера`() = runBlocking {
        var now = 0L
        val remote = FakeRemote(listOf(record(1)))
        val repository = ReadingsRepository(FakeLocal(), remote, cacheTtlMillis = 10_000, clock = { now })

        repository.refresh()
        now = 5_000
        val second = repository.refresh()

        assertEquals(RefreshOutcome.CACHE_FRESH, second.getOrNull())
        assertEquals(1, remote.fetchCalls)
    }

    @Test
    fun `після закінчення TTL дані оновлюються, а force ігнорує TTL`() = runBlocking {
        var now = 0L
        val remote = FakeRemote(listOf(record(1)))
        val repository = ReadingsRepository(FakeLocal(), remote, cacheTtlMillis = 10_000, clock = { now })

        repository.refresh()
        now = 10_001
        repository.refresh()
        repository.refresh(force = true)

        assertEquals(3, remote.fetchCalls)
    }

    @Test
    fun `якщо сервер недоступний, кеш лишається і повертається помилка`() = runBlocking {
        val remote = FakeRemote(listOf(record(1)))
        val local = FakeLocal()
        val repository = ReadingsRepository(local, remote)
        repository.refresh()

        remote.failing = true
        val result = repository.refresh(force = true)

        assertTrue(result.isFailure)
        assertEquals(1, local.items.value.size)
    }

    @Test
    fun `запис потрапляє в кеш лише після успіху на сервері`() = runBlocking {
        val remote = FakeRemote(emptyList(), failing = true)
        val local = FakeLocal()
        val repository = ReadingsRepository(local, remote)

        val failed = repository.add(record(1))
        assertTrue(failed.isFailure)
        assertTrue(local.items.value.isEmpty())

        remote.failing = false
        val saved = repository.add(record(1))
        assertTrue(saved.isSuccess)
        assertEquals(1, local.items.value.size)
    }

    @Test
    fun `оновлення і видалення синхронізують сервер та кеш`() = runBlocking {
        val remote = FakeRemote(listOf(record(1), record(2)))
        val local = FakeLocal()
        val repository = ReadingsRepository(local, remote)
        repository.refresh()

        repository.update(record(1).copy(voltage = 220.0))
        repository.delete(2)

        assertEquals(220.0, local.items.value.single().voltage, 0.0001)
        assertEquals(1, remote.data.size)
    }

    @Test
    fun `прогноз без історії спирається на типовий профіль, з історією на останні три записи`() = runBlocking {
        val remote = FakeRemote(listOf(record(1), record(2), record(3)))
        val local = FakeLocal()
        val repository = ReadingsRepository(local, remote)

        val withoutHistory = repository.forecastKw()
        assertTrue(withoutHistory > 0.0)

        repository.refresh()
        // три однакові записи 230 В * 10 А * 0.95 = 2.185 кВт
        assertEquals(2.185, repository.forecastKw(), 0.0001)
        assertEquals(3, repository.currentRecords().size)
        assertEquals(3, local.observeAll().first().size)
    }
}
