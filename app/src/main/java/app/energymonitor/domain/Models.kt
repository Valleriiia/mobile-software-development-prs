package app.energymonitor.domain

/** Миттєві показання лічильника: напруга (В), струм (А), cos(fi) (0..1). */
data class MeterReading(
    val voltage: Double,
    val current: Double,
    val powerFactor: Double
)

/** Одна точка добового профілю: година доби 0..23 і потужність, кВт. */
data class ConsumptionPoint(
    val hour: Int,
    val powerKw: Double
)

/** Окремий споживач (лінія/пристрій) на панелі моніторингу. */
data class DeviceLoad(
    val name: String,
    val powerKw: Double,
    val isOn: Boolean
)

/** Режим роботи об'єкта — впливає і на рівень навантаження (factor),
 *  і на тариф. */
enum class OperationMode(
    val title: String,
    val factor: Double,
    val tariff: Double,
    val comment: String
) {
    NIGHT("Нічний", 0.45, 2.16, "Мінімальне навантаження, діє нічний тариф"),
    DAY("Денний", 1.00, 4.32, "Типове денне навантаження об'єкта"),
    PEAK("Піковий", 1.60, 6.48, "Години максимуму — тариф найвищий")
}

/** Рівень навантаження відносно договірної потужності. */
enum class LoadLevel(val title: String) {
    NORMAL("Норма"),
    WARNING("Підвищене"),
    OVERLOAD("Перевантаження")
}
