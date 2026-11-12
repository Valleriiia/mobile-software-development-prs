package app.energymonitor.data.live

import app.energymonitor.data.network.RetryPolicy
import app.energymonitor.domain.EnergyCalculator
import app.energymonitor.domain.LiveEvent
import app.energymonitor.domain.LiveSample
import app.energymonitor.domain.MeterReading
import app.energymonitor.domain.SensorException
import app.energymonitor.domain.SensorSignal
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.zip

/**
 * Огортає значення датчика в SensorSignal і додає автоматичний повторний збір.
 *
 * retryWhen знову підписується на "холодний" потік датчика після збою. Лямбда
 * виконується всередині FlowCollector, тому через emit(...) можна повідомити
 * нижчі етапи, що йде повторна спроба, а потім чекати delay(...). Пауза зростає
 * експоненційно за тією самою RetryPolicy, що й для HTTP-запитів. Після
 * вичерпання спроб (або для збоїв, що не є SensorException) виняток іде далі.
 */
fun <T> Flow<T>.withSensorRetry(
    sensor: String,
    policy: RetryPolicy
): Flow<SensorSignal<T>> =
    map<T, SensorSignal<T>> { SensorSignal.Value(it) }
        .retryWhen { cause, attempt ->
            val failedAttempt = attempt.toInt() + 1 // retryWhen нумерує з нуля, політика з одиниці
            if (cause is SensorException && failedAttempt < policy.maxAttempts) {
                val pause = policy.delayAfterAttempt(failedAttempt)
                emit(SensorSignal.Retrying(sensor, failedAttempt, pause))
                delay(pause)
                true
            } else {
                false
            }
        }

/**
 * Конвеєр живих даних. Об'єднує п'ять джерел різної природи:
 *  - датчики напруги й струму: zip, тобто строгі пари "одне вимірювання U + одне I";
 *  - лічильник cos(fi): combine, бо він повільніший і береться останнє відоме значення;
 *  - модель прогнозу: ковзне середнє за живими показаннями (стан у flow-блоці);
 *  - історія з Room (baseline): ще один потік, об'єднаний combine.
 *
 * Результат є "холодним" потоком LiveEvent; "гарячим" його робить shareIn у ViewModel,
 * щоб кілька підписників ділили один набір датчиків.
 */
class LiveMeterFeed(
    private val sensors: MeterSensors,
    private val baseline: Flow<Double?>,
    private val retryPolicy: RetryPolicy = RetryPolicy(maxAttempts = 4, initialDelayMillis = 500),
    private val limitKw: Double = EnergyCalculator.CONTRACT_POWER_KW,
    private val forecastWindow: Int = FORECAST_WINDOW,
    private val clock: () -> Long = System::currentTimeMillis
) {

    fun events(): Flow<LiveEvent> {
        val voltage = sensors.voltage().withSensorRetry(SimulatedMeterSensors.VOLTAGE_SENSOR, retryPolicy)
        val current = sensors.current().withSensorRetry(SimulatedMeterSensors.CURRENT_SENSOR, retryPolicy)
        val powerFactor = sensors.powerFactor().withSensorRetry(SimulatedMeterSensors.PF_SENSOR, retryPolicy)

        val pairs = voltage.zip(current) { u, i -> u to i }

        return combine(pairs, powerFactor, baseline.onStart { emit(null) }) { (u, i), pf, base ->
            toRaw(u, i, pf, base)
        }
            // Хибні показання (глюк датчика) відсіюються, а не доходять до графіка.
            .filter { it !is Raw.Invalid }
            .withForecast()
            .distinctUntilChanged()
            // Збій, що не вилікувався повторами, перетворюється на подію, а не на аварію.
            .catch { error ->
                val sensor = (error as? SensorException)?.sensor
                emit(LiveEvent.Failed(sensor, error.message ?: "Невідома помилка датчика"))
            }
    }

    /** Проміжний результат злиття трьох сигналів до обчислення прогнозу. */
    private sealed interface Raw {
        data class Reading(
            val voltage: Double,
            val current: Double,
            val powerFactor: Double,
            val baselineKw: Double?
        ) : Raw

        data class Retry(val event: LiveEvent.Degraded) : Raw
        object Invalid : Raw
    }

    private fun toRaw(
        u: SensorSignal<Double>,
        i: SensorSignal<Double>,
        pf: SensorSignal<Double>,
        baselineKw: Double?
    ): Raw {
        listOf(u, i, pf).firstOrNull { it is SensorSignal.Retrying }?.let {
            val retry = it as SensorSignal.Retrying
            return Raw.Retry(LiveEvent.Degraded(retry.sensor, retry.attempt, retry.nextDelayMillis))
        }
        val voltage = (u as SensorSignal.Value).value
        val current = (i as SensorSignal.Value).value
        val factor = (pf as SensorSignal.Value).value
        // Фізично неможливі значення вважаємо збоєм вимірювання.
        val valid = voltage in MIN_VOLTAGE..MAX_VOLTAGE && current >= 0.0 && factor in 0.0..1.0
        return if (valid) Raw.Reading(voltage, current, factor, baselineKw) else Raw.Invalid
    }

    /**
     * Модель прогнозу: ковзне середнє останніх `forecastWindow` потужностей.
     * Стан (вікно) створюється всередині flow-блоку, тобто окремо для кожного
     * підписника, тож два збирання потоку не псують один одному дані.
     */
    private fun Flow<Raw>.withForecast(): Flow<LiveEvent> {
        val upstream = this
        return flow {
            val window = ArrayDeque<Double>()
            upstream.collect { raw ->
                when (raw) {
                    is Raw.Retry -> emit(raw.event)
                    Raw.Invalid -> Unit
                    is Raw.Reading -> {
                        val power = EnergyCalculator.activePowerKw(
                            MeterReading(raw.voltage, raw.current, raw.powerFactor)
                        )
                        // Першому показанню прогнозувати нема з чого: прогноз дорівнює йому самому.
                        val trend = if (window.isEmpty()) power else window.average()
                        window.addLast(power)
                        if (window.size > forecastWindow) window.removeFirst()
                        emit(
                            LiveEvent.Sample(
                                LiveSample(
                                    voltage = raw.voltage,
                                    current = raw.current,
                                    powerFactor = raw.powerFactor,
                                    powerKw = power,
                                    trendForecastKw = trend,
                                    baselineKw = raw.baselineKw,
                                    deviationPercent = EnergyCalculator.deviationPercent(power, trend),
                                    level = EnergyCalculator.loadLevel(power, limitKw),
                                    timestampMillis = clock()
                                )
                            )
                        )
                    }
                }
            }
        }
    }

    companion object {
        const val FORECAST_WINDOW = 5
        private const val MIN_VOLTAGE = 100.0
        private const val MAX_VOLTAGE = 400.0
    }
}
