package app.energymonitor.domain

/**
 * Один запис показника лічильника, введений користувачем.
 * id = 0 означає "ще не збережено в базі" (Room сам призначить справжній id).
 */
data class ReadingRecord(
    val id: Long = 0,
    val label: String,
    val timestampMillis: Long,
    val voltage: Double,
    val current: Double,
    val powerFactor: Double
) {
    val activePowerKw: Double
        get() = EnergyCalculator.activePowerKw(MeterReading(voltage, current, powerFactor))
}
