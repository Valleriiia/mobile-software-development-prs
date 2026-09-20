package app.energymonitor.domain

import kotlin.math.abs
import kotlin.math.sqrt

/** Розрахунки системи моніторингу енергоспоживання. Об'єкт-одинак — не
 *  потребує створення екземпляра і легко покривається unit-тестами. */
object EnergyCalculator {

    /** Договірна (дозволена) потужність об'єкта, кВт. */
    const val CONTRACT_POWER_KW = 6.0

    /** P = U * I * cos(fi) / 1000 */
    fun activePowerKw(reading: MeterReading): Double =
        reading.voltage * reading.current * reading.powerFactor / 1000.0

    /** S = U * I / 1000 */
    fun apparentPowerKva(reading: MeterReading): Double =
        reading.voltage * reading.current / 1000.0

    /** Q = sqrt(S^2 - P^2). Через похибки округлення S може вийти трохи
     *  меншим за P, тому від'ємне значення під коренем відсікається. */
    fun reactivePowerKvar(reading: MeterReading): Double {
        val s = apparentPowerKva(reading)
        val p = activePowerKw(reading)
        val diff = s * s - p * p
        return if (diff <= 0.0) 0.0 else sqrt(diff)
    }

    fun energyKwh(powerKw: Double, hours: Double): Double = powerKw * hours

    fun cost(energyKwh: Double, tariff: Double): Double = energyKwh * tariff

    /** Крок профілю дорівнює 1 годині, тому сума потужностей = кВт*год. */
    fun dailyEnergyKwh(profile: List<ConsumptionPoint>): Double =
        profile.sumOf { it.powerKw }

    fun averagePowerKw(profile: List<ConsumptionPoint>): Double =
        if (profile.isEmpty()) 0.0 else profile.sumOf { it.powerKw } / profile.size

    fun peakPoint(profile: List<ConsumptionPoint>): ConsumptionPoint? =
        profile.maxByOrNull { it.powerKw }

    fun minPoint(profile: List<ConsumptionPoint>): ConsumptionPoint? =
        profile.minByOrNull { it.powerKw }

    /** Pсер / Pмакс — чим ближче до 1, тим рівномірніше об'єкт споживає
     *  енергію протягом доби (менше пікових перепадів). */
    fun loadFactor(profile: List<ConsumptionPoint>): Double {
        val max = peakPoint(profile)?.powerKw ?: return 0.0
        return if (max == 0.0) 0.0 else averagePowerKw(profile) / max
    }

    fun loadLevel(powerKw: Double, limitKw: Double = CONTRACT_POWER_KW): LoadLevel = when {
        powerKw >= limitKw -> LoadLevel.OVERLOAD
        powerKw >= limitKw * 0.75 -> LoadLevel.WARNING
        else -> LoadLevel.NORMAL
    }

    /** Ковзне середнє за останні `window` годин — найпростіший робочий
     *  прогноз, якого достатньо для навчального проєкту. */
    fun forecastNextHourKw(profile: List<ConsumptionPoint>, window: Int = 3): Double {
        if (profile.isEmpty()) return 0.0
        val tail = profile.takeLast(window)
        return tail.sumOf { it.powerKw } / tail.size
    }

    /** Відхилення факту від прогнозу, % — використовується для виявлення
     *  аномального стрибка навантаження. */
    fun deviationPercent(actualKw: Double, forecastKw: Double): Double =
        if (forecastKw == 0.0) 0.0 else abs(actualKw - forecastKw) / forecastKw * 100.0
}
