package app.energymonitor.data.live

import app.energymonitor.domain.SensorException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Джерела живих даних лічильника. Кожен метод повертає "холодний" потік:
 * вимірювання починаються лише тоді, коли хтось підписується, а кожна нова
 * підписка запускає датчик заново. Реалізацію можна підмінити простим
 * flowOf(...) у unit-тестах, тож решта конвеєра від імітації не залежить.
 */
interface MeterSensors {
    fun voltage(): Flow<Double>
    fun current(): Flow<Double>

    /** Лічильник cos(fi) повільніший за датчики U та I, тому живе окремим потоком. */
    fun powerFactor(): Flow<Double>
}

/** Режими збоїв для перевірки повторних спроб (лише на панелі розробника). */
enum class SensorFault(val title: String) {
    NONE("Без збоїв"),
    TRANSIENT("Тимчасовий"),
    PERMANENT("Постійний")
}

/**
 * Імітація датчиків: напруга коливається біля 230 В, струм повільно "дихає"
 * за синусоїдою (щоб інколи траплялися підвищене навантаження і перевантаження),
 * cos(fi) змінюється зрідка. Зрідка датчик напруги видає хибний нуль (глюк),
 * який конвеєр відсіює оператором filter.
 */
class SimulatedMeterSensors(
    private val intervalMillis: Long = 1000L,
    private val random: Random = Random.Default,
    private val glitchProbability: Double = 0.04
) : MeterSensors {

    @Volatile
    var fault: SensorFault = SensorFault.NONE
        set(value) {
            field = value
            // Тимчасовий збій: наступні два запуски датчика струму завершаться помилкою, третій пройде.
            transientBudget.set(if (value == SensorFault.TRANSIENT) TRANSIENT_FAILURES else 0)
        }

    private val transientBudget = AtomicInteger(0)

    override fun voltage(): Flow<Double> = flow {
        while (true) {
            val glitch = random.nextDouble() < glitchProbability
            emit(if (glitch) 0.0 else 230.0 + random.nextDouble(-4.0, 4.0))
            delay(intervalMillis)
        }
    }

    override fun current(): Flow<Double> = flow {
        val startedAt = System.currentTimeMillis()
        while (true) {
            failIfFaulty()
            val seconds = (System.currentTimeMillis() - startedAt) / 1000.0
            // Базове навантаження 16 А, амплітуда 11 А, період близько 80 с, плюс шум.
            val value = 16.0 + 11.0 * sin(2 * PI * seconds / 80.0) + random.nextDouble(-1.0, 1.0)
            emit(value.coerceAtLeast(0.5))
            delay(intervalMillis)
        }
    }

    override fun powerFactor(): Flow<Double> = flow {
        var value = 0.94
        while (true) {
            emit(value)
            delay(intervalMillis * 3)
            value = (value + random.nextDouble(-0.02, 0.02)).coerceIn(0.88, 0.99)
        }
    }

    private fun failIfFaulty() {
        when (fault) {
            SensorFault.NONE -> Unit
            SensorFault.PERMANENT -> throw SensorException(CURRENT_SENSOR, "Датчик струму не відповідає")
            SensorFault.TRANSIENT ->
                if (transientBudget.getAndUpdate { if (it > 0) it - 1 else 0 } > 0) {
                    throw SensorException(CURRENT_SENSOR, "Тимчасовий збій датчика струму")
                }
        }
    }

    companion object {
        const val CURRENT_SENSOR = "датчик струму"
        const val VOLTAGE_SENSOR = "датчик напруги"
        const val PF_SENSOR = "лічильник cos(fi)"
        private const val TRANSIENT_FAILURES = 2
    }
}
