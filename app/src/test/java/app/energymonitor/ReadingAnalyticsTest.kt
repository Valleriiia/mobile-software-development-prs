package app.energymonitor

import app.energymonitor.domain.LoadLevel
import app.energymonitor.domain.ReadingAnalytics
import app.energymonitor.domain.ReadingRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReadingAnalyticsTest {

    private fun reading(id: Long, label: String, voltage: Double, current: Double, pf: Double) =
        ReadingRecord(id, label, id, voltage, current, pf)

    @Test
    fun `порожній список дає нульову статистику`() {
        val stats = ReadingAnalytics.aggregate(emptyList())
        assertEquals(0, stats.count)
        assertNull(stats.peakLabel)
    }

    @Test
    fun `агрегація рахує середнє, максимум та рівні навантаження`() {
        val records = listOf(
            reading(1, "А", 230.0, 10.0, 1.0),  // 2.30 кВт  — норма
            reading(2, "Б", 230.0, 20.0, 1.0),  // 4.60 кВт  — підвищене (>= 4.5)
            reading(3, "В", 230.0, 30.0, 1.0)   // 6.90 кВт  — перевантаження (>= 6)
        )

        val stats = ReadingAnalytics.aggregate(records)

        assertEquals(3, stats.count)
        assertEquals(4.6, stats.averagePowerKw, 0.0001)
        assertEquals(6.9, stats.peakPowerKw, 0.0001)
        assertEquals("В", stats.peakLabel)
        assertEquals(1, stats.warningCount)
        assertEquals(1, stats.overloadCount)
    }

    @Test
    fun `рівень навантаження залежить від переданої договірної потужності`() {
        val record = reading(1, "А", 230.0, 20.0, 1.0) // 4.6 кВт
        assertEquals(LoadLevel.WARNING, ReadingAnalytics.levelOf(record, limitKw = 6.0))
        assertEquals(LoadLevel.OVERLOAD, ReadingAnalytics.levelOf(record, limitKw = 4.0))
        assertEquals(LoadLevel.NORMAL, ReadingAnalytics.levelOf(record, limitKw = 10.0))
    }
}
