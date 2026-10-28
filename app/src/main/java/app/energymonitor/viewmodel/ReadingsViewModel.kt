package app.energymonitor.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.energymonitor.data.ReadingsRepository
import app.energymonitor.data.RefreshOutcome
import app.energymonitor.domain.LoadLevel
import app.energymonitor.domain.ReadingAnalytics
import app.energymonitor.domain.ReadingRecord
import app.energymonitor.domain.ReadingStats
import app.energymonitor.domain.ReadingValidator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * MVVM-шар для екранів показників. ViewModel не знає, скільки джерел даних
 * існує: вона працює лише з ReadingsRepository і готує для UI вже відфільтрований,
 * відсортований список та агреговану статистику.
 */
class ReadingsViewModel(private val repository: ReadingsRepository) : ViewModel() {

    private val _filterQuery = MutableStateFlow("")
    val filterQuery: StateFlow<String> = _filterQuery.asStateFlow()

    private val _sortOrder = MutableStateFlow(SortOrder.NEWEST)
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    private val _levelFilter = MutableStateFlow<LoadLevel?>(null)
    val levelFilter: StateFlow<LoadLevel?> = _levelFilter.asStateFlow()

    private val _sourceMode = MutableStateFlow(DataSourceMode.AGGREGATED)
    val sourceMode: StateFlow<DataSourceMode> = _sourceMode.asStateFlow()

    private val _remoteSnapshot = MutableStateFlow<List<ReadingRecord>>(emptyList())

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    // Режим вирішує, який із двох потоків піде далі; решта конвеєра
    // (фільтр, сортування, статистика) від режиму вже не залежить.
    private val sourceData: Flow<List<ReadingRecord>> =
        combine(repository.observeAll(), _remoteSnapshot, _sourceMode) { local, remote, mode ->
            if (mode == DataSourceMode.REMOTE) remote else local
        }

    val readings: StateFlow<List<ReadingRecord>> =
        combine(sourceData, _filterQuery, _sortOrder, _levelFilter) { list, query, order, level ->
            val byName = if (query.isBlank()) list
            else list.filter { it.label.contains(query, ignoreCase = true) }

            val byLevel = if (level == null) byName
            else byName.filter { ReadingAnalytics.levelOf(it) == level }

            when (order) {
                SortOrder.NEWEST -> byLevel.sortedByDescending { it.timestampMillis }
                SortOrder.OLDEST -> byLevel.sortedBy { it.timestampMillis }
                SortOrder.POWER_DESC -> byLevel.sortedByDescending { it.activePowerKw }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val stats: StateFlow<ReadingStats> = readings
        .map { ReadingAnalytics.aggregate(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReadingStats.EMPTY)

    init {
        refresh(force = false)
    }

    fun setFilterQuery(query: String) {
        _filterQuery.value = query
    }

    fun setSortOrder(order: SortOrder) {
        _sortOrder.value = order
    }

    fun setLevelFilter(level: LoadLevel?) {
        _levelFilter.value = level
    }

    fun setSourceMode(mode: DataSourceMode) {
        _sourceMode.value = mode
        when (mode) {
            DataSourceMode.REMOTE -> loadRemoteSnapshot()
            DataSourceMode.AGGREGATED -> refresh(force = false)
            DataSourceMode.LOCAL -> _statusMessage.value = "Показано лише локальний кеш"
        }
    }

    fun refresh(force: Boolean = true) {
        viewModelScope.launch {
            _isRefreshing.value = true
            _statusMessage.value = repository.refresh(force).fold(
                onSuccess = { outcome ->
                    when (outcome) {
                        RefreshOutcome.CACHE_FRESH -> "Дані актуальні (кеш)"
                        RefreshOutcome.UPDATED -> "Оновлено із сервера"
                    }
                },
                onFailure = { "Сервер недоступний, показано кеш: ${errorText(it)}" }
            )
            _isRefreshing.value = false
        }
    }

    private fun loadRemoteSnapshot() {
        viewModelScope.launch {
            _isRefreshing.value = true
            repository.fetchRemoteSnapshot().fold(
                onSuccess = {
                    _remoteSnapshot.value = it
                    _statusMessage.value = "Показано дані сервера"
                },
                onFailure = { _statusMessage.value = "Не вдалося отримати дані сервера: ${errorText(it)}" }
            )
            _isRefreshing.value = false
        }
    }

    fun addReading(label: String, voltageText: String, currentText: String, powerFactorText: String) {
        viewModelScope.launch {
            // Список для перевірки унікальності беремо зі знімка кешу, а не з
            // readings.value: той порожній, поки жоден екран не підписаний на потік.
            val validation = ReadingValidator.validate(
                label = label,
                existingLabels = repository.currentRecords().map { it.label },
                voltageText = voltageText,
                currentText = currentText,
                powerFactorText = powerFactorText
            )
            if (!validation.isSuccess) {
                _uiState.value = UiState.Error(validation.message)
                return@launch
            }

            _uiState.value = UiState.Loading
            val record = ReadingRecord(
                id = System.currentTimeMillis(),
                label = label.trim(),
                timestampMillis = System.currentTimeMillis(),
                voltage = voltageText.replace(',', '.').toDouble(),
                current = currentText.replace(',', '.').toDouble(),
                powerFactor = powerFactorText.replace(',', '.').toDouble()
            )
            _uiState.value = repository.add(record).fold(
                onSuccess = { UiState.Success(validation.message) },
                onFailure = { UiState.Error("Не вдалося зберегти: ${errorText(it)}") }
            )
        }
    }

    fun updateReading(original: ReadingRecord, voltageText: String, currentText: String, powerFactorText: String) {
        viewModelScope.launch {
            // Назва не змінюється, тому унікальність перевіряємо серед ІНШИХ записів.
            val validation = ReadingValidator.validate(
                label = original.label,
                existingLabels = repository.currentRecords().filter { it.id != original.id }.map { it.label },
                voltageText = voltageText,
                currentText = currentText,
                powerFactorText = powerFactorText
            )
            if (!validation.isSuccess) {
                _uiState.value = UiState.Error(validation.message)
                return@launch
            }

            _uiState.value = UiState.Loading
            val updated = original.copy(
                timestampMillis = System.currentTimeMillis(),
                voltage = voltageText.replace(',', '.').toDouble(),
                current = currentText.replace(',', '.').toDouble(),
                powerFactor = powerFactorText.replace(',', '.').toDouble()
            )
            _uiState.value = repository.update(updated).fold(
                onSuccess = { UiState.Success("Показник «${updated.label}» оновлено") },
                onFailure = { UiState.Error("Не вдалося оновити: ${errorText(it)}") }
            )
        }
    }

    fun deleteReading(id: Long) {
        viewModelScope.launch {
            repository.delete(id).onFailure {
                _uiState.value = UiState.Error("Не вдалося видалити: ${errorText(it)}")
            }
        }
    }

    suspend fun forecastKw(): Double = repository.forecastKw()

    fun resetUiState() {
        _uiState.value = UiState.Idle
    }

    private fun errorText(error: Throwable): String = error.message ?: error::class.java.simpleName
}

class ReadingsViewModelFactory(
    private val repository: ReadingsRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        ReadingsViewModel(repository) as T
}
