package app.energymonitor.domain

data class Meter(
    val id: String,
    val name: String,
    val building: String,
    val floor: Int,
    val contractPowerKw: Double
)

data class Tariff(
    val zone: String,
    val pricePerKwh: Double,
    val hoursPerDay: Int
)

data class MeterCardData(
    val meter: Meter,
    val powerKw: Double?,
    val loadPercent: Double?,
    val level: LoadLevel?,
    val dailyEnergyKwh: Double?,
    val dailyCostUah: Double?
)

data class FleetSummary(
    val meterCount: Int,
    val totalPowerKw: Double,
    val totalContractKw: Double,
    val utilizationPercent: Double,
    val overloadedCount: Int,
    val withoutDataCount: Int,
    val totalDailyCostUah: Double
) {
    companion object {
        val EMPTY = FleetSummary(0, 0.0, 0.0, 0.0, 0, 0, 0.0)
    }
}

/**
 * Зведення даних із двох ендпоінтів (довідник точок обліку + показники) і
 * тарифів. Чисті функції без залежностей від мережі та UI.
 */
object MeterAnalytics {

    /**
     * Вартість доби за незмінного навантаження: потужність множиться на години
     * кожної тарифної зони та її ціну.
     */
    fun dailyCostUah(powerKw: Double, tariffs: List<Tariff>): Double =
        tariffs.sumOf { powerKw * it.hoursPerDay * it.pricePerKwh }

    fun buildCards(
        meters: List<Meter>,
        readings: List<ReadingRecord>,
        tariffs: List<Tariff>
    ): List<MeterCardData> = meters.map { meter ->
        // Показник прив'язується до точки обліку за назвою; береться найсвіжіший.
        val latest = readings
            .filter { it.label.trim().equals(meter.name.trim(), ignoreCase = true) }
            .maxByOrNull { it.timestampMillis }

        if (latest == null) {
            MeterCardData(meter, null, null, null, null, null)
        } else {
            val power = latest.activePowerKw
            MeterCardData(
                meter = meter,
                powerKw = power,
                loadPercent = if (meter.contractPowerKw > 0) power / meter.contractPowerKw * 100.0 else null,
                level = EnergyCalculator.loadLevel(power, meter.contractPowerKw),
                dailyEnergyKwh = power * HOURS_PER_DAY,
                dailyCostUah = dailyCostUah(power, tariffs)
            )
        }
    }

    fun summarize(cards: List<MeterCardData>): FleetSummary {
        if (cards.isEmpty()) return FleetSummary.EMPTY
        val withData = cards.filter { it.powerKw != null }
        val totalPower = withData.sumOf { it.powerKw ?: 0.0 }
        val totalContract = cards.sumOf { it.meter.contractPowerKw }
        return FleetSummary(
            meterCount = cards.size,
            totalPowerKw = totalPower,
            totalContractKw = totalContract,
            utilizationPercent = if (totalContract > 0) totalPower / totalContract * 100.0 else 0.0,
            overloadedCount = cards.count { it.level == LoadLevel.OVERLOAD },
            withoutDataCount = cards.size - withData.size,
            totalDailyCostUah = withData.sumOf { it.dailyCostUah ?: 0.0 }
        )
    }

    private const val HOURS_PER_DAY = 24.0
}
