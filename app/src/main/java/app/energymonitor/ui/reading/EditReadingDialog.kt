package app.energymonitor.ui.reading

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.energymonitor.domain.ReadingRecord
import app.energymonitor.domain.fmt

/**
 * Діалог редагування показника. Назва точки обліку не редагується — вона
 * є ключем запису, тому змінюються лише виміряні значення.
 * Сам діалог нічого не зберігає: він віддає введений текст через onConfirm,
 * а перевірку й збереження виконує ViewModel.
 */
@Composable
fun EditReadingDialog(
    record: ReadingRecord,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onConfirm: (voltage: String, current: String, powerFactor: String) -> Unit
) {
    var voltage by remember { mutableStateOf(record.voltage.fmt(1)) }
    var current by remember { mutableStateOf(record.current.fmt(1)) }
    var powerFactor by remember { mutableStateOf(record.powerFactor.fmt(2)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Редагування: ${record.label}") },
        text = {
            Column {
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
                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    FormMessage(message = errorMessage, isError = true)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(voltage, current, powerFactor) }) { Text("Зберегти") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Скасувати") }
        }
    )
}
