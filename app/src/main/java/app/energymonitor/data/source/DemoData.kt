package app.energymonitor.data.source

import app.energymonitor.domain.ReadingRecord

object DemoData {

    private const val HOUR = 60 * 60 * 1000L

    fun readings(now: Long = System.currentTimeMillis()): List<ReadingRecord> = listOf(
        ReadingRecord(1, "Щит №1", now - 1 * HOUR, 230.0, 12.0, 0.95),
        ReadingRecord(2, "Щит №2", now - 2 * HOUR, 228.0, 8.0, 0.90),
        ReadingRecord(3, "Серверна", now - 3 * HOUR, 231.0, 22.0, 0.97),
        ReadingRecord(4, "Освітлення", now - 4 * HOUR, 229.0, 5.0, 0.92),
        ReadingRecord(5, "Вентиляція", now - 5 * HOUR, 232.0, 28.0, 0.97)
    )
}
