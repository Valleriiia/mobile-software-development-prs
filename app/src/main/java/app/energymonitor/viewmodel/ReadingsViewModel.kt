package app.energymonitor.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.energymonitor.data.ReadingsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import app.energymonitor.domain.ReadingRecord
import app.energymonitor.domain.ReadingValidator
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * MVVM-шар для екранів "Додати показник" / "Історія показників".
 * ViewModel не знає нічого про Compose — лише про Repository та domain-логіку,
 * тому екрани підписуються на readings/uiState через collectAsState().
 */
class ReadingsViewModel(private val repository: ReadingsRepository) : ViewModel() {

    private val _filterQuery = MutableStateFlow("")
    val filterQuery: StateFlow<String> = _filterQuery.asStateFlow()

    private val _sortOrder = MutableStateFlow(SortOrder.NEWEST)
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    // combine перераховує список щоразу, як зміниться будь-яке з трьох джерел:
    // самі дані з Room, текст фільтра або обраний порядок сортування.
    val readings: StateFlow<List<ReadingRecord>> =
        combine(repository.observeAll(), _filterQuery, _sortOrder) { list, query, order ->
            val filtered = if (query.isBlank()) list
            else list.filter { it.label.contains(query, ignoreCase = true) }

            when (order) {
                SortOrder.NEWEST -> filtered.sortedByDescending { it.timestampMillis }
                SortOrder.OLDEST -> filtered.sortedBy { it.timestampMillis }
                SortOrder.POWER_DESC -> filtered.sortedByDescending { it.activePowerKw }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setFilterQuery(query: String) {
        _filterQuery.value = query
    }

    fun setSortOrder(order: SortOrder) {
        _sortOrder.value = order
    }

    fun addReading(label: String, voltageText: String, currentText: String, powerFactorText: String) {
        // виклик бізнес-логіки (домену) з ViewModel
        val validation = ReadingValidator.validate(
            label = label,
            existingLabels = readings.value.map { it.label },
            voltageText = voltageText,
            currentText = currentText,
            powerFactorText = powerFactorText
        )
        if (!validation.isSuccess) {
            _uiState.value = UiState.Error(validation.message)
            return
        }

        val currentTime = System.currentTimeMillis()
        // Асинхронна операція у viewModelScope (автоматично скасовується при знищенні ViewModel)
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            delay(700)
            repository.insert(
                ReadingRecord(
                    id = currentTime,
                    label = label.trim(),
                    timestampMillis = System.currentTimeMillis(),
                    voltage = voltageText.replace(',', '.').toDouble(),
                    current = currentText.replace(',', '.').toDouble(),
                    powerFactor = powerFactorText.replace(',', '.').toDouble()
                )
            )
            // Оновлення стану викликає реактивне перемальовування UI в Compose
            _uiState.value = UiState.Success(validation.message)
        }
    }

    fun deleteReading(id: Long) {
        viewModelScope.launch { repository.delete(id) }
    }

    suspend fun forecastKw(): Double = repository.forecastKw()

    fun resetUiState() {
        _uiState.value = UiState.Idle
    }
}

class ReadingsViewModelFactory(
    private val repository: ReadingsRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        ReadingsViewModel(repository) as T
}
