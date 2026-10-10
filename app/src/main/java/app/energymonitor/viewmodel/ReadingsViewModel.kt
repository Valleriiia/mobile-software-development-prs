package app.energymonitor.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import app.energymonitor.domain.ReadingRecord
import app.energymonitor.domain.ReadingValidator

/**
 * MVVM-шар для екранів "Додати показник" / "Історія показників".
 * ViewModel не знає нічого про Compose — лише про Repository та domain-логіку,
 * тому екрани підписуються на readings/uiState через collectAsState().
 */
class ReadingsViewModel : ViewModel() {

    // Інкапсуляція стану: Mutable змінюється лише тут, UI отримує незмінний StateFlow
    private val _readings = MutableStateFlow<List<ReadingRecord>>(emptyList())
    val readings: StateFlow<List<ReadingRecord>> = _readings.asStateFlow()

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun addReading(label: String, voltageText: String, currentText: String, powerFactorText: String) {
        // виклик бізнес-логіки (домену) з ViewModel
        val validation = ReadingValidator.validate(
            label = label,
            existingLabels = _readings.value.map { it.label },
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
            _readings.value += ReadingRecord(
                id = currentTime,
                label = label.trim(),
                timestampMillis = System.currentTimeMillis(),
                voltage = voltageText.replace(',', '.').toDouble(),
                current = currentText.replace(',', '.').toDouble(),
                powerFactor = powerFactorText.replace(',', '.').toDouble()
            )
            // Оновлення стану викликає реактивне перемальовування UI в Compose
            _uiState.value = UiState.Success(validation.message)
        }
    }

    fun resetUiState() {
        _uiState.value = UiState.Idle
    }
}
