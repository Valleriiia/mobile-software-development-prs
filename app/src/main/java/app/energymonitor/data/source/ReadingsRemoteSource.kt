package app.energymonitor.data.source

import app.energymonitor.domain.ReadingRecord

/**
 * Віддалене джерело даних (хмарне сховище показників). Методи кидають
 * виняток при збої, а перетворення його на Result виконує Repository —
 * так джерела лишаються простими, а політика обробки помилок живе в одному місці.
 */
interface ReadingsRemoteSource {
    suspend fun fetchAll(): List<ReadingRecord>
    suspend fun add(record: ReadingRecord): ReadingRecord
    suspend fun update(record: ReadingRecord): ReadingRecord
    suspend fun delete(id: Long)
}
