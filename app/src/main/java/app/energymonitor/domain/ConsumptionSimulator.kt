package app.energymonitor.domain

import kotlin.random.Random

/**
 * Імітація джерела даних (у продакшн-версії тут був би Retrofit- або
 * MQTT-клієнт). Логіка відокремлена від UI, щоб екранам було байдуже,
 * звідки саме надходять дані.
 */
object ConsumptionSimulator {

    // Коефіцієнти типового добового профілю (0..1) по годинах 00:00-23:00:
    // мінімум вночі, максимуми зранку та ввечері.
    private val DAILY_SHAPE = doubleArrayOf(
        0.30, 0.26, 0.24, 0.23, 0.25, 0.35,
        0.55, 0.78, 0.85, 0.72, 0.66, 0.64,
        0.68, 0.70, 0.65, 0.63, 0.70, 0.86,
        1.00, 0.95, 0.88, 0.72, 0.55, 0.38
    )

    /**
     * Генерує добовий профіль споживання з невеликим випадковим розкидом.
     *
     * @param peakKw максимальна потужність об'єкта, кВт
     * @param mode режим роботи, який масштабує весь графік
     */
    fun dailyProfile(
        peakKw: Double = 5.4,
        mode: OperationMode = OperationMode.DAY
    ): List<ConsumptionPoint> =
        DAILY_SHAPE.mapIndexed { hour, k ->
            val noise = Random.nextDouble(-0.06, 0.06)
            val value = peakKw * mode.factor * (k + noise)
            ConsumptionPoint(hour = hour, powerKw = value.coerceAtLeast(0.05))
        }

    /** Генерує "миттєві" показання лічильника — імітація опитування пристрою. */
    fun randomReading(): MeterReading = MeterReading(
        voltage = Random.nextDouble(218.0, 232.0),
        current = Random.nextDouble(4.0, 28.0),
        powerFactor = Random.nextDouble(0.86, 0.99)
    )

    fun devices(mode: OperationMode): List<DeviceLoad> = listOf(
        DeviceLoad("Освітлення", 0.42 * mode.factor, true),
        DeviceLoad("Опалення / клімат", 2.15 * mode.factor, mode != OperationMode.NIGHT),
        DeviceLoad("Серверна стійка", 1.10, true),
        DeviceLoad("Вентиляція", 0.75 * mode.factor, mode != OperationMode.NIGHT),
        DeviceLoad("Зарядна станція EV", 1.80 * mode.factor, mode == OperationMode.PEAK)
    )
}
