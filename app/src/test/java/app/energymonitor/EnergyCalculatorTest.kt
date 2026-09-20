package app.energymonitor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import app.energymonitor.domain.AuthValidator
import app.energymonitor.domain.ConsumptionPoint
import app.energymonitor.domain.EnergyCalculator
import app.energymonitor.domain.LoadLevel
import app.energymonitor.domain.MeterReading

class EnergyCalculatorTest {

    @Test
    fun `активна потужність обчислюється правильно`() {
        // U = 220 В, I = 10 А, cos(fi) = 0.95  ->  P = 2.09 кВт
        val reading = MeterReading(voltage = 220.0, current = 10.0, powerFactor = 0.95)
        assertEquals(2.09, EnergyCalculator.activePowerKw(reading), 0.001)
    }

    @Test
    fun `повна потужність не менша за активну`() {
        val reading = MeterReading(230.0, 12.0, 0.9)
        val p = EnergyCalculator.activePowerKw(reading)
        val s = EnergyCalculator.apparentPowerKva(reading)
        assertTrue(s >= p)
    }

    @Test
    fun `вартість енергії рахується за тарифом`() {
        val energy = EnergyCalculator.energyKwh(powerKw = 2.0, hours = 5.0) // 10 кВт*год
        assertEquals(43.2, EnergyCalculator.cost(energy, 4.32), 0.001)
    }

    @Test
    fun `рівень навантаження визначається коректно`() {
        assertEquals(LoadLevel.NORMAL, EnergyCalculator.loadLevel(2.0))
        assertEquals(LoadLevel.WARNING, EnergyCalculator.loadLevel(5.0))
        assertEquals(LoadLevel.OVERLOAD, EnergyCalculator.loadLevel(6.5))
    }

    @Test
    fun `добове споживання дорівнює сумі погодинних значень`() {
        val profile = listOf(
            ConsumptionPoint(0, 1.0),
            ConsumptionPoint(1, 2.0),
            ConsumptionPoint(2, 3.0)
        )
        assertEquals(6.0, EnergyCalculator.dailyEnergyKwh(profile), 0.001)
        assertEquals(2.0, EnergyCalculator.averagePowerKw(profile), 0.001)
        assertEquals(3.0, EnergyCalculator.peakPoint(profile)?.powerKw ?: 0.0, 0.001)
    }

    @Test
    fun `валідація форми входу відхиляє короткий пароль`() {
        val result = AuthValidator.validateLogin("operator", "123")
        assertTrue(!result.isSuccess)
    }

    @Test
    fun `валідація реєстрації виявляє розбіжність паролів`() {
        val result = AuthValidator.validateRegistration(
            name = "Оператор",
            email = "op@kpi.ua",
            password = "energy2026",
            confirmPassword = "energy2025"
        )
        assertTrue(!result.isSuccess)
    }
}
