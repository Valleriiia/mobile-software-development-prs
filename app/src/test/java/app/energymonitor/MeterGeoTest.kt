package app.energymonitor

import app.energymonitor.data.remote.dto.LocationDto
import app.energymonitor.data.remote.dto.MeterDto
import app.energymonitor.data.remote.dto.toDomainOrNull
import app.energymonitor.domain.GeoBounds
import app.energymonitor.domain.GeoPoint
import app.energymonitor.domain.MeterType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MeterGeoTest {

    private fun dto(location: LocationDto?, type: String? = "hvac", description: String? = " Опис ") =
        MeterDto("m1", "Тест", location, 6.0, type, description)

    @Test
    fun `координати з вкладеного location потрапляють у доменну модель`() {
        val meter = dto(LocationDto("Корпус 1", 2, 50.45, 30.46)).toDomainOrNull()!!
        assertEquals(GeoPoint(50.45, 30.46), meter.position)
        assertEquals(MeterType.HVAC, meter.type)
        assertEquals("Опис", meter.description) // пробіли обрізано
    }

    @Test
    fun `некоректні або неповні координати дають null, а не аварію`() {
        assertNull(dto(LocationDto("К", 1, 95.0, 30.0)).toDomainOrNull()!!.position)   // широта поза межами
        assertNull(dto(LocationDto("К", 1, 50.0, 200.0)).toDomainOrNull()!!.position)  // довгота поза межами
        assertNull(dto(LocationDto("К", 1, 50.0, null)).toDomainOrNull()!!.position)   // немає довготи
        assertNull(dto(null).toDomainOrNull()!!.position)                              // немає location
    }

    @Test
    fun `невідомий тип стає OTHER`() {
        assertEquals(MeterType.OTHER, dto(null, type = "unknown").toDomainOrNull()!!.type)
        assertEquals(MeterType.OTHER, dto(null, type = null).toDomainOrNull()!!.type)
        assertEquals(MeterType.SERVER_ROOM, MeterType.fromApi(" SERVER_ROOM "))
    }

    @Test
    fun `межі охоплюють усі точки`() {
        val bounds = GeoBounds.of(
            listOf(GeoPoint(50.45010, 30.46140), GeoPoint(50.44940, 30.46420), GeoPoint(50.45022, 30.46128))
        )!!
        assertEquals(50.44940, bounds.south, 1e-9)
        assertEquals(50.45022, bounds.north, 1e-9)
        assertEquals(30.46128, bounds.west, 1e-9)
        assertEquals(30.46420, bounds.east, 1e-9)
        assertEquals(50.44981, bounds.center.latitude, 1e-9)
        assertFalse(bounds.isSinglePoint)
    }

    @Test
    fun `порожній набір не має меж, одна точка не масштабується під рамку`() {
        assertNull(GeoBounds.of(emptyList()))
        assertTrue(GeoBounds.of(listOf(GeoPoint(1.0, 2.0)))!!.isSinglePoint)
    }
}
