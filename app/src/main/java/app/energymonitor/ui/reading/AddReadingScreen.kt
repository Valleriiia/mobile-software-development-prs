package app.energymonitor.ui.reading

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.energymonitor.ui.nav.ScreenHeader
import app.energymonitor.viewmodel.ReadingsViewModel
import app.energymonitor.viewmodel.UiState

/**
 * Composable-екран для додавання нового показника.
 * Взаємодіє з ViewModel для збереження даних та відображення статусу операції.
 *
 * @param viewModel ViewModel для управління даними показників
 * @param onBack Функція для повернення на попередній екран
 */
@Composable
fun AddReadingScreen(
    viewModel: ReadingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var label by remember { mutableStateOf("") }
    var voltage by remember { mutableStateOf("230") }
    var current by remember { mutableStateOf("10") }
    var powerFactor by remember { mutableStateOf("0.95") }

    // Реактивна підписка на StateFlow. UI автоматично оновлюється при зміні стану у ViewModel
    val uiState by viewModel.uiState.collectAsState()

    // Обробка побічних ефектів (side-effects) на основі стану з ViewModel
    LaunchedEffect(uiState) {
        if (uiState is UiState.Success) label = ""
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        ScreenHeader(title = "Додати показник", onBack = onBack)
        Spacer(modifier = Modifier.height(12.dp))
        FormHeader(
            title = "Додати показник",
            subtitle = "Дані перевіряються і зберігаються у локальній базі Room"
        )
        Spacer(modifier = Modifier.height(16.dp))

        LabeledField(
            value = label,
            onValueChange = { label = it },
            label = "Назва точки обліку",
            hint = "наприклад: Щит №1"
        )
        Spacer(modifier = Modifier.height(8.dp))
        LabeledField(
            value = voltage,
            onValueChange = { voltage = it },
            label = "Напруга, В",
            hint = "діапазон 100–400",
            keyboardType = KeyboardType.Decimal
        )
        Spacer(modifier = Modifier.height(8.dp))
        LabeledField(
            value = current,
            onValueChange = { current = it },
            label = "Струм, А",
            hint = "діапазон 0–200",
            keyboardType = KeyboardType.Decimal
        )
        Spacer(modifier = Modifier.height(8.dp))
        LabeledField(
            value = powerFactor,
            onValueChange = { powerFactor = it },
            label = "cos(fi)",
            hint = "значення від 0 до 1",
            keyboardType = KeyboardType.Decimal
        )

        Spacer(modifier = Modifier.height(16.dp))

        when (val state = uiState) {
            is UiState.Loading -> FormLoadingIndicator()
            is UiState.Error -> FormMessage(state.message, isError = true)
            is UiState.Success -> FormMessage(state.message, isError = false)
            UiState.Idle -> {}
        }

        Spacer(modifier = Modifier.height(8.dp))

        SubmitButton(
            text = "Зберегти показник",
            enabled = uiState !is UiState.Loading,
            // Передача дії користувача (intent) у ViewModel для виконання бізнес-логіки
            onClick = { viewModel.addReading(label, voltage, current, powerFactor) }
        )
    }
}
