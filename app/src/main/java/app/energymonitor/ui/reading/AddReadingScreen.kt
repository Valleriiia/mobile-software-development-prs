package app.energymonitor.ui.reading

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import app.energymonitor.domain.ReadingRecord
import app.energymonitor.domain.ReadingValidator
import app.energymonitor.ui.nav.ScreenHeader

@Composable
fun AddReadingScreen(
    existingLabels: List<String>,
    onSubmit: (ReadingRecord) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var label by remember { mutableStateOf("") }
    var voltage by remember { mutableStateOf("230") }
    var current by remember { mutableStateOf("10") }
    var powerFactor by remember { mutableStateOf("0.95") }
    var isLoading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        ScreenHeader(title = "Додати показник", onBack = onBack)
        Spacer(modifier = Modifier.height(12.dp))
        FormHeader(title = "Додати показник", subtitle = "Значення перевіряються перед збереженням")
        Spacer(modifier = Modifier.height(16.dp))

        LabeledField(value = label, onValueChange = { label = it }, label = "Назва точки обліку", hint = "наприклад: Щит №1")
        Spacer(modifier = Modifier.height(8.dp))
        LabeledField(value = voltage, onValueChange = { voltage = it }, label = "Напруга, В", hint = "діапазон 100–400", keyboardType = KeyboardType.Decimal)
        Spacer(modifier = Modifier.height(8.dp))
        LabeledField(value = current, onValueChange = { current = it }, label = "Струм, А", hint = "діапазон 0–200", keyboardType = KeyboardType.Decimal)
        Spacer(modifier = Modifier.height(8.dp))
        LabeledField(value = powerFactor, onValueChange = { powerFactor = it }, label = "cos(fi)", hint = "значення від 0 до 1", keyboardType = KeyboardType.Decimal)

        Spacer(modifier = Modifier.height(16.dp))

        if (isLoading) FormLoadingIndicator() else FormMessage(message, isError)

        Spacer(modifier = Modifier.height(8.dp))

        SubmitButton(
            text = "Зберегти показник",
            enabled = !isLoading,
            onClick = {
                val validation = ReadingValidator.validate(label, existingLabels, voltage, current, powerFactor)
                if (!validation.isSuccess) {
                    isError = true
                    message = validation.message
                } else {
                    scope.launch {
                        isLoading = true
                        delay(700)
                        onSubmit(
                            ReadingRecord(
                                label = label.trim(),
                                timestampMillis = System.currentTimeMillis(),
                                voltage = voltage.replace(',', '.').toDouble(),
                                current = current.replace(',', '.').toDouble(),
                                powerFactor = powerFactor.replace(',', '.').toDouble()
                            )
                        )
                        isError = false
                        message = validation.message
                        label = ""
                        isLoading = false
                    }
                }
            }
        )
    }
}