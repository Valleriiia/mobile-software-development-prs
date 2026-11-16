package app.energymonitor.data.remote

import app.energymonitor.data.remote.dto.LocationDto
import app.energymonitor.data.remote.dto.MeterDto
import app.energymonitor.data.remote.dto.MetersResponse
import app.energymonitor.data.remote.dto.ReadingDto
import app.energymonitor.data.remote.dto.TariffDto
import app.energymonitor.data.remote.dto.toDto
import app.energymonitor.data.source.DemoData

/** Збої, які мок-сервер може імітувати, щоб перевіряти реакцію застосунку. */
enum class FailureMode(val title: String) {
    NONE("Без збоїв"),
    FLAKY("Нестабільний"),
    SERVER_ERROR("Помилка 500"),
    OFFLINE("Немає мережі"),
    TIMEOUT("Таймаут")
}

/** Імітація серверної частини в пам'яті застосунку: зберігає дані та
 * виконує серверні бізнес-правила (валідація, перевірка унікальності).
 * MockServerInterceptor формує HTTP-відповіді на основі цього класу,
 * тому весь мережевий шар (Retrofit, Gson, Repository) працює як зі справжнім API. */
class MockBackend(now: Long = System.currentTimeMillis()) {

    @Volatile
    var failureMode: FailureMode = FailureMode.NONE

    @Volatile
    var latencyMillis: Long = 400

    private val lock = Any()
    private val readings: MutableList<ReadingDto> =
        DemoData.readings(now).map { it.toDto() }.toMutableList()
    private var flakyStreak = 0
    private val updatedAt = now

    // Координати умовні (поблизу кампусу), різниця між точками одного корпусу кілька метрів.
    val meters: List<MeterDto> = listOf(
        MeterDto(
            "m1", "Щит №1", LocationDto("Корпус 18", 1, 50.45010, 30.46140), 6.0,
            "switchboard", "Головний розподільчий щит першого поверху"
        ),
        MeterDto(
            "m2", "Щит №2", LocationDto("Корпус 18", 2, 50.45016, 30.46150), 4.0,
            "switchboard", "Щит живлення лабораторій другого поверху"
        ),
        MeterDto(
            "m3", "Серверна", LocationDto("Корпус 18", 0, 50.45004, 30.46128), 6.0,
            "server_room", "Серверна кімната з резервним живленням (ДБЖ)"
        ),
        MeterDto(
            "m4", "Освітлення", LocationDto("Корпус 18", 1, 50.45022, 30.46132), 2.0,
            "lighting", "Лінія освітлення коридорів та аудиторій"
        ),
        MeterDto(
            "m5", "Вентиляція", LocationDto("Корпус 20", 3, 50.44940, 30.46420), 6.0,
            "hvac", "Припливно-витяжна вентиляція верхнього поверху"
        )
    )

    val tariffs: List<TariffDto> = listOf(
        TariffDto("NIGHT", 2.16, 8),
        TariffDto("DAY", 4.32, 12),
        TariffDto("PEAK", 6.48, 4)
    )

    /** У режимі FLAKY кожен запит двічі поспіль отримує збій, а третій проходить. */
    fun shouldFailFlaky(): Boolean = synchronized(lock) {
        if (flakyStreak < FLAKY_FAILURES) {
            flakyStreak++
            true
        } else {
            flakyStreak = 0
            false
        }
    }

    fun listReadings(): List<ReadingDto> = synchronized(lock) { readings.toList() }

    fun metersResponse() = MetersResponse(meters, updatedAt)

    /** null означає, що дані коректні; інакше — текст помилки валідації. */
    fun validate(dto: ReadingDto): String? {
        val label = dto.label
        if (label.isNullOrBlank()) return "Назва точки обліку обов'язкова"
        val voltage = dto.voltage
        if (voltage == null || voltage !in 100.0..400.0) return "Напруга поза межами 100–400 В"
        val current = dto.current
        if (current == null || current <= 0.0 || current > 200.0) return "Струм поза межами 0–200 А"
        val powerFactor = dto.powerFactor
        if (powerFactor == null || powerFactor !in 0.0..1.0) return "cos(fi) має бути від 0 до 1"
        return null
    }

    fun labelTaken(label: String, exceptId: Long?): Boolean = synchronized(lock) {
        readings.any { it.label.equals(label.trim(), ignoreCase = true) && it.id != exceptId }
    }

    fun addReading(dto: ReadingDto): ReadingDto = synchronized(lock) {
        val id = dto.id ?: ((readings.maxOfOrNull { it.id ?: 0L } ?: 0L) + 1)
        val saved = dto.copy(id = id, label = dto.label?.trim())
        readings.removeAll { it.id == id }
        readings.add(saved)
        saved
    }

    fun updateReading(id: Long, dto: ReadingDto): ReadingDto? = synchronized(lock) {
        val index = readings.indexOfFirst { it.id == id }
        if (index < 0) {
            null
        } else {
            val saved = dto.copy(id = id, label = dto.label?.trim())
            readings[index] = saved
            saved
        }
    }

    fun deleteReading(id: Long): Boolean = synchronized(lock) { readings.removeAll { it.id == id } }

    private companion object {
        const val FLAKY_FAILURES = 2
    }
}
