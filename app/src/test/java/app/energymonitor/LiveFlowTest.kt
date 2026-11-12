package app.energymonitor

import app.energymonitor.data.live.LiveMeterFeed
import app.energymonitor.data.live.MeterSensors
import app.energymonitor.data.live.SensorFault
import app.energymonitor.data.live.SimulatedMeterSensors
import app.energymonitor.data.live.levelAlerts
import app.energymonitor.data.network.RetryPolicy
import app.energymonitor.domain.LiveEvent
import app.energymonitor.domain.LiveSample
import app.energymonitor.domain.LoadLevel
import app.energymonitor.domain.SensorException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/** Датчики зі сценарієм: кожен метод повертає потік, заданий тестом. */
private class ScriptedSensors(
    private val voltage: () -> Flow<Double>,
    private val current: () -> Flow<Double>,
    private val powerFactor: () -> Flow<Double> = { flowOf(1.0) }
) : MeterSensors {
    override fun voltage() = voltage.invoke()
    override fun current() = current.invoke()
    override fun powerFactor() = powerFactor.invoke()
}

/** Значення виходять з паузою; cos(fi) встигає надійти раніше за першу пару U/I. */
private fun paced(vararg values: Double): Flow<Double> = flow {
    delay(8)
    for (value in values) {
        emit(value)
        delay(3)
    }
}

private fun sample(level: LoadLevel) = LiveEvent.Sample(
    LiveSample(230.0, 10.0, 0.9, 2.0, 2.0, null, 0.0, level, 0L)
)

class LiveFlowTest {

    private val fast = RetryPolicy(maxAttempts = 4, initialDelayMillis = 2, backoffFactor = 2.0)

    private fun feed(sensors: MeterSensors, policy: RetryPolicy = fast) =
        LiveMeterFeed(
            sensors = sensors,
            baseline = emptyFlow(),
            retryPolicy = policy,
            clock = { 0L }
        )

    private fun List<LiveEvent>.samples() = filterIsInstance<LiveEvent.Sample>().map { it.sample }

    @Test
    fun `напруга і струм зіставляються строгими парами`() = runBlocking {
        val events = feed(
            ScriptedSensors(
                voltage = { paced(230.0, 220.0) },
                current = { paced(10.0, 20.0) },
                powerFactor = { flowOf(0.9) }
            )
        ).events().toList()

        val samples = events.samples()
        assertEquals(2, samples.size)
        // Перша пара 230 В / 10 А, друга 220 В / 20 А: пари не перемішуються.
        assertEquals(230.0 * 10.0 * 0.9 / 1000.0, samples[0].powerKw, 1e-9)
        assertEquals(220.0 * 20.0 * 0.9 / 1000.0, samples[1].powerKw, 1e-9)
    }

    @Test
    fun `хибні показання датчика відсіюються оператором filter`() = runBlocking {
        val events = feed(
            ScriptedSensors(
                voltage = { paced(0.0, 230.0, 999.0, 231.0) },
                current = { paced(10.0, 10.0, 10.0, 10.0) }
            )
        ).events().toList()

        val voltages = events.samples().map { it.voltage }
        assertEquals(listOf(230.0, 231.0), voltages)
    }

    @Test
    fun `прогноз тренду - ковзне середнє попередніх показань`() = runBlocking {
        val events = feed(
            ScriptedSensors(
                voltage = { paced(200.0, 200.0, 200.0) },
                current = { paced(10.0, 20.0, 30.0) } // потужності 2, 4, 6 кВт при cos(fi) = 1
            )
        ).events().toList()

        val samples = events.samples()
        assertEquals(3, samples.size)
        assertEquals(2.0, samples[0].trendForecastKw, 1e-9) // історії немає: прогноз = сам показник
        assertEquals(2.0, samples[1].trendForecastKw, 1e-9) // середнє з {2}
        assertEquals(3.0, samples[2].trendForecastKw, 1e-9) // середнє з {2, 4}
        assertEquals(LoadLevel.OVERLOAD, samples[2].level)  // 6 кВт досягає договірної межі
        assertEquals(100.0, samples[2].deviationPercent, 1e-9) // |6 - 3| / 3
    }

    @Test
    fun `датчик відновлюється після тимчасових збоїв з експоненційною паузою`() = runBlocking {
        val starts = AtomicInteger(0)
        val events = feed(
            ScriptedSensors(
                voltage = { paced(*DoubleArray(30) { 230.0 }) },
                // Лічильник запусків рахується всередині flow {}: кожна повторна
                // підписка виконує блок заново, як і справжній холодний потік датчика.
                current = {
                    flow {
                        if (starts.incrementAndGet() <= 2) throw SensorException("датчик струму", "збій")
                        emitAll(paced(*DoubleArray(30) { 10.0 }))
                    }
                }
            )
        ).events().toList()

        val degraded = events.filterIsInstance<LiveEvent.Degraded>()
        assertEquals(listOf(1, 2), degraded.map { it.attempt })
        assertEquals(listOf(2L, 4L), degraded.map { it.nextDelayMillis }) // 2 мс, потім 4 мс
        assertTrue(events.none { it is LiveEvent.Failed })
        assertTrue(events.samples().isNotEmpty())
        assertEquals(3, starts.get()) // дві невдалі підписки й одна успішна
    }

    @Test
    fun `після вичерпання спроб потік повідомляє про збій, а не падає`() = runBlocking {
        val starts = AtomicInteger(0)
        val events = feed(
            ScriptedSensors(
                voltage = { paced(*DoubleArray(30) { 230.0 }) },
                current = {
                    flow {
                        starts.incrementAndGet()
                        throw SensorException("датчик струму", "Датчик струму не відповідає")
                    }
                }
            ),
            policy = RetryPolicy(maxAttempts = 3, initialDelayMillis = 1)
        ).events().toList()

        val last = events.last()
        assertTrue(last is LiveEvent.Failed)
        assertEquals("датчик струму", (last as LiveEvent.Failed).sensor)
        assertEquals(3, starts.get()) // рівно maxAttempts спроб
        assertEquals(2, events.filterIsInstance<LiveEvent.Degraded>().size)
        assertTrue(events.samples().isEmpty())
    }

    @Test
    fun `помилка не з датчика не повторюється`() = runBlocking {
        val starts = AtomicInteger(0)
        val events = feed(
            ScriptedSensors(
                voltage = { paced(230.0) },
                current = {
                    flow {
                        starts.incrementAndGet()
                        throw IllegalStateException("баг у коді")
                    }
                }
            )
        ).events().toList()

        assertEquals(1, starts.get())
        assertTrue(events.last() is LiveEvent.Failed)
    }

    @Test
    fun `два одночасні підписники ділять один запуск датчиків`() = runBlocking {
        val starts = AtomicInteger(0)
        val cold = flow {
            starts.incrementAndGet()
            for (n in 0 until 20) {
                emit(n)
                delay(2)
            }
        }
        val scope = CoroutineScope(Dispatchers.Default)
        val hot = cold.shareIn(scope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 200), replay = 1)

        val first = async { hot.first { it >= 5 } }
        val second = async { hot.first { it >= 5 } }
        first.await()
        second.await()
        scope.cancel()

        assertEquals(1, starts.get())
    }

    @Test
    fun `сповіщення подавляють миготіння рівня і ігнорують норму`() = runBlocking {
        val events = flow {
            // Швидке миготіння: NORMAL -> WARNING -> OVERLOAD, до кінця паузи лишається OVERLOAD.
            emit(sample(LoadLevel.NORMAL))
            emit(sample(LoadLevel.WARNING))
            emit(sample(LoadLevel.OVERLOAD))
            delay(120)
            emit(sample(LoadLevel.NORMAL)) // норма сповіщення не потребує
            delay(120)
            emit(sample(LoadLevel.OVERLOAD))
            emit(sample(LoadLevel.OVERLOAD)) // повтор того самого рівня не рахується
            delay(120)
        }
        val alerts = events.levelAlerts(debounceMillis = 40).toList()
        assertEquals(listOf(LoadLevel.OVERLOAD, LoadLevel.OVERLOAD), alerts)
    }

    @Test
    fun `тимчасовий збій імітатора минає з третьої спроби`() = runBlocking {
        val sensors = SimulatedMeterSensors(intervalMillis = 1)
        sensors.fault = SensorFault.TRANSIENT
        repeat(2) {
            try {
                sensors.current().first()
                fail("очікувався збій датчика")
            } catch (e: SensorException) {
                assertEquals("датчик струму", e.sensor)
            }
        }
        val value = sensors.current().first()
        assertTrue(value in 0.5..30.0)
    }

    @Test
    fun `постійний збій імітатора не минає`() = runBlocking {
        val sensors = SimulatedMeterSensors(intervalMillis = 1)
        sensors.fault = SensorFault.PERMANENT
        repeat(3) {
            try {
                sensors.current().first()
                fail("очікувався збій датчика")
            } catch (e: SensorException) {
                // очікувано
            }
        }
        sensors.fault = SensorFault.NONE
        assertTrue(sensors.current().first() > 0.0)
    }
}
