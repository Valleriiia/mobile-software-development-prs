package app.energymonitor.domain

/**
 * Одне комплексне показання "наживо": результат об'єднання кількох незалежних
 * джерел (датчик напруги, датчик струму, лічильник cos(fi), модель прогнозу
 * та історія з бази). Лише дані, жодної логіки відображення.
 */
data class LiveSample(
    val voltage: Double,
    val current: Double,
    val powerFactor: Double,
    val powerKw: Double,
    /** Прогноз ковзного середнього за останніми живими показаннями. */
    val trendForecastKw: Double,
    /** Прогноз на основі збережених показників (Room); null, якщо історії немає. */
    val baselineKw: Double?,
    /** Відхилення поточної потужності від прогнозу тренду, %. */
    val deviationPercent: Double,
    val level: LoadLevel,
    val timestampMillis: Long
)

/** Події гарячого потоку: або нове показання, або стан збою датчика. */
sealed interface LiveEvent {
    data class Sample(val sample: LiveSample) : LiveEvent

    /** Датчик не відповів, триває повторна спроба з експоненційною затримкою. */
    data class Degraded(val sensor: String, val attempt: Int, val nextDelayMillis: Long) : LiveEvent

    /** Спроби вичерпано: потік завершено, потрібне ручне перезапускання. */
    data class Failed(val sensor: String?, val message: String) : LiveEvent
}

/** Виняток, яким датчик повідомляє про збій; лише такі збої повторюються. */
class SensorException(val sensor: String, message: String) : RuntimeException(message)

/** Обгортка значення датчика: або дані, або повідомлення про повторну спробу. */
sealed interface SensorSignal<out T> {
    data class Value<T>(val value: T) : SensorSignal<T>
    data class Retrying(val sensor: String, val attempt: Int, val nextDelayMillis: Long) : SensorSignal<Nothing>
}
