package app.energymonitor.domain

data class ReadingStats(
    val count: Int,
    val averagePowerKw: Double,
    val peakPowerKw: Double,
    val peakLabel: String?,
    val warningCount: Int,
    val overloadCount: Int
) {
    companion object {
        val EMPTY = ReadingStats(0, 0.0, 0.0, null, 0, 0)
    }
}

/** Агрегація даних: чиста функція без залежностей від UI чи бази, тому легко тестується. */
object ReadingAnalytics {

    fun levelOf(record: ReadingRecord, limitKw: Double = EnergyCalculator.CONTRACT_POWER_KW): LoadLevel =
        EnergyCalculator.loadLevel(record.activePowerKw, limitKw)

    fun aggregate(
        records: List<ReadingRecord>,
        limitKw: Double = EnergyCalculator.CONTRACT_POWER_KW
    ): ReadingStats {
        val peak = records.maxByOrNull { it.activePowerKw } ?: return ReadingStats.EMPTY
        val levels = records.map { levelOf(it, limitKw) }
        return ReadingStats(
            count = records.size,
            averagePowerKw = records.sumOf { it.activePowerKw } / records.size,
            peakPowerKw = peak.activePowerKw,
            peakLabel = peak.label,
            warningCount = levels.count { it == LoadLevel.WARNING },
            overloadCount = levels.count { it == LoadLevel.OVERLOAD }
        )
    }
}
