package app.energymonitor.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.energymonitor.data.MetersRepository
import app.energymonitor.data.MetersSnapshot
import app.energymonitor.data.ReadingsRepository
import app.energymonitor.domain.FleetSummary
import app.energymonitor.domain.LoadLevel
import app.energymonitor.domain.MeterAnalytics
import app.energymonitor.domain.MeterCardData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class MeterSort(val title: String) {
    LOAD_DESC("За навантаженням"),
    COST_DESC("За вартістю"),
    NAME("За назвою")
}

data class MetersUiState(
    val cards: List<MeterCardData> = emptyList(),
    val summary: FleetSummary = FleetSummary.EMPTY,
    val isLoading: Boolean = false,
    val message: String? = null,
    val isStale: Boolean = false
)

/**
 * Збирає екран із двох незалежних джерел: довідник точок обліку з REST API
 * та показники з Room. Картки перераховуються автоматично, коли змінюється
 * будь-яке з них.
 */
class MetersViewModel(
    private val metersRepository: MetersRepository,
    private val readingsRepository: ReadingsRepository
) : ViewModel() {

    private val snapshot = MutableStateFlow<MetersSnapshot?>(null)

    private val _sort = MutableStateFlow(MeterSort.LOAD_DESC)
    val sort: StateFlow<MeterSort> = _sort.asStateFlow()

    private val _levelFilter = MutableStateFlow<LoadLevel?>(null)
    val levelFilter: StateFlow<LoadLevel?> = _levelFilter.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    private val _message = MutableStateFlow<String?>(null)

    private val allCards: Flow<List<MeterCardData>> =
        combine(snapshot, readingsRepository.observeAll()) { snap, readings ->
            if (snap == null) emptyList()
            else MeterAnalytics.buildCards(snap.meters, readings, snap.tariffs)
        }

    val state: StateFlow<MetersUiState> =
        combine(allCards, _sort, _levelFilter, _isLoading, _message) { cards, sort, level, loading, message ->
            val filtered = if (level == null) cards else cards.filter { it.level == level }
            val sorted = when (sort) {
                MeterSort.LOAD_DESC -> filtered.sortedByDescending { it.loadPercent ?: -1.0 }
                MeterSort.COST_DESC -> filtered.sortedByDescending { it.dailyCostUah ?: -1.0 }
                MeterSort.NAME -> filtered.sortedBy { it.meter.name }
            }
            MetersUiState(
                cards = sorted,
                // Підсумок завжди за всіма точками, а не за відфільтрованими.
                summary = MeterAnalytics.summarize(cards),
                isLoading = loading,
                message = message,
                isStale = snapshot.value?.isStale == true
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MetersUiState())

    init {
        load(force = false)
    }

    fun setSort(sort: MeterSort) {
        _sort.value = sort
    }

    fun setLevelFilter(level: LoadLevel?) {
        _levelFilter.value = level
    }

    fun load(force: Boolean = true) {
        viewModelScope.launch {
            _isLoading.value = true
            // Картки будуються з показників у Room, тому оновлюємо й їх.
            readingsRepository.refresh(force)
            metersRepository.load(force).fold(
                onSuccess = { snap ->
                    snapshot.value = snap
                    _message.value = if (snap.isStale) "Сервер недоступний, показано збережені дані" else null
                },
                onFailure = { error ->
                    _message.value = "Не вдалося завантажити точки обліку: ${error.message ?: error::class.java.simpleName}"
                }
            )
            _isLoading.value = false
        }
    }
}

class MetersViewModelFactory(
    private val metersRepository: MetersRepository,
    private val readingsRepository: ReadingsRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        MetersViewModel(metersRepository, readingsRepository) as T
}
