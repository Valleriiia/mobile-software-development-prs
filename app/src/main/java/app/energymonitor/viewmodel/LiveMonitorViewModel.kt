package app.energymonitor.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.energymonitor.data.ReadingsRepository
import app.energymonitor.data.live.LiveMeterFeed
import app.energymonitor.data.live.SensorFault
import app.energymonitor.data.live.SimulatedMeterSensors
import app.energymonitor.data.live.levelAlerts
import app.energymonitor.domain.LiveEvent
import app.energymonitor.domain.LiveSample
import app.energymonitor.domain.LoadLevel
import app.energymonitor.domain.fmt
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn

enum class LiveConnection { CONNECTING, LIVE, RETRYING, FAILED }

data class LiveUiState(
    val sample: LiveSample? = null,
    /** Останні потужності для графіка (кВт) і відповідні прогнози тренду. */
    val powerHistory: List<Double> = emptyList(),
    val forecastHistory: List<Double> = emptyList(),
    val connection: LiveConnection = LiveConnection.CONNECTING,
    val statusText: String = "Підключення до датчиків…",
    val running: Boolean = true
)

/**
 * MVVM-шар екрана "Моніторинг у реальному часі".
 *
 * Потік даних:
 *   датчики (cold) -> LiveMeterFeed.events() -> shareIn (hot SharedFlow) -> два підписники:
 *     1) стан екрана (scan згортає події в LiveUiState -> StateFlow);
 *     2) сповіщення про зміну рівня навантаження (levelAlerts -> Snackbar).
 * Обидва підписники ділять один набір датчиків, бо потік "гарячий".
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LiveMonitorViewModel(
    private val sensors: SimulatedMeterSensors,
    repository: ReadingsRepository,
    private val messenger: AppMessenger,
    private val feed: LiveMeterFeed = LiveMeterFeed(sensors, repository.observeForecastKw())
) : ViewModel() {

    private val _running = MutableStateFlow(true)

    /** Лічильник перезапусків: нове значення змушує flatMapLatest створити датчики заново. */
    private val restartCounter = MutableStateFlow(0)

    private val _fault = MutableStateFlow(sensors.fault)
    val fault: StateFlow<SensorFault> = _fault.asStateFlow()

    /**
     * Гарячий потік. WhileSubscribed(5 с): поки є хоча б один підписник, датчики
     * працюють; через 5 с після останнього (пауза, вихід з екрана) їх буде
     * зупинено, і кеш повторного програвання очиститься, щоб після відновлення
     * не показувалося застаріле значення.
     */
    private val hot: SharedFlow<LiveEvent> = restartCounter
        .flatMapLatest { feed.events() }
        .shareIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000, replayExpirationMillis = 0),
            replay = 1
        )

    /** Пауза: просто відписуємося від гарячого потоку, датчики зупиняться самі. */
    private fun <T> Flow<T>.whileRunning(): Flow<T> =
        _running.flatMapLatest { running -> if (running) this else emptyFlow() }

    val state: StateFlow<LiveUiState> = combine(
        hot.whileRunning().scan(LiveUiState()) { previous, event -> reduce(previous, event) },
        _running
    ) { ui, running -> ui.copy(running = running) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LiveUiState())

    init {
        hot.whileRunning()
            .levelAlerts(ALERT_DEBOUNCE_MILLIS)
            .onEach { level ->
                val power = state.value.sample?.powerKw
                val suffix = power?.let { ": ${it.fmt(2)} кВт" }.orEmpty()
                messenger.info(
                    if (level == LoadLevel.OVERLOAD) "Перевантаження$suffix" else "Підвищене навантаження$suffix"
                )
            }
            .launchIn(viewModelScope)
    }

    fun togglePause() {
        _running.value = !_running.value
    }

    /** Ручний перезапуск після остаточного збою датчиків. */
    fun restart() {
        _running.value = true
        restartCounter.value += 1
    }

    fun setFault(fault: SensorFault) {
        sensors.fault = fault
        _fault.value = fault
        // Збій видно лише після перезапуску датчика, тож перезапускаємо потік.
        restartCounter.value += 1
    }

    private fun reduce(previous: LiveUiState, event: LiveEvent): LiveUiState = when (event) {
        is LiveEvent.Sample -> previous.copy(
            sample = event.sample,
            powerHistory = (previous.powerHistory + event.sample.powerKw).takeLast(HISTORY_SIZE),
            forecastHistory = (previous.forecastHistory + event.sample.trendForecastKw).takeLast(HISTORY_SIZE),
            connection = LiveConnection.LIVE,
            statusText = "Дані надходять у реальному часі"
        )
        is LiveEvent.Degraded -> previous.copy(
            connection = LiveConnection.RETRYING,
            statusText = "${event.sensor.replaceFirstChar { it.uppercase() }}: спроба ${event.attempt} невдала, " +
                "повтор через ${(event.nextDelayMillis / 1000.0).fmt(1)} с"
        )
        is LiveEvent.Failed -> previous.copy(
            connection = LiveConnection.FAILED,
            statusText = "Збій: ${event.message}. Спроби вичерпано"
        )
    }

    private companion object {
        const val HISTORY_SIZE = 40
        const val ALERT_DEBOUNCE_MILLIS = 1_500L
    }
}

class LiveMonitorViewModelFactory(
    private val sensors: SimulatedMeterSensors,
    private val repository: ReadingsRepository,
    private val messenger: AppMessenger
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        LiveMonitorViewModel(sensors, repository, messenger) as T
}
