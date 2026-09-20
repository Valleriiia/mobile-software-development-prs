package app.energymonitor.ui.calculator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.energymonitor.domain.ConsumptionPoint
import app.energymonitor.domain.ConsumptionSimulator
import app.energymonitor.domain.EnergyCalculator
import app.energymonitor.domain.MeterReading
import app.energymonitor.domain.OperationMode
import app.energymonitor.domain.asHourLabel
import app.energymonitor.domain.fmt

/** Результат розрахунку, який показується користувачу. */
private data class CalculationResult(
    val activePowerKw: Double,
    val apparentPowerKva: Double,
    val reactivePowerKvar: Double,
    val energyKwh: Double,
    val costUah: Double
)

@Composable
fun CalculatorScreen(modifier: Modifier = Modifier) {

    var voltage by remember { mutableStateOf("230") }
    var current by remember { mutableStateOf("12.5") }
    var powerFactor by remember { mutableStateOf("0.95") }
    var hours by remember { mutableStateOf("8") }

    var mode by remember { mutableStateOf(OperationMode.DAY) }
    var result by remember { mutableStateOf<CalculationResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    var profile by remember { mutableStateOf<List<ConsumptionPoint>>(emptyList()) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "Розрахунок споживання",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Параметри вводяться вручну, обчислення виконує EnergyCalculator",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            NumberField(
                value = voltage,
                onValueChange = { voltage = it },
                label = "Напруга U, В",
                modifier = Modifier.weight(1f)
            )
            NumberField(
                value = current,
                onValueChange = { current = it },
                label = "Струм I, А",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            NumberField(
                value = powerFactor,
                onValueChange = { powerFactor = it },
                label = "cos(fi)",
                modifier = Modifier.weight(1f)
            )
            NumberField(
                value = hours,
                onValueChange = { hours = it },
                label = "Час роботи, год",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Режим роботи об'єкта:",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OperationMode.values().forEach { item ->
                // Обраний режим показуємо заповненою кнопкою, решту — з контуром
                if (item == mode) {
                    Button(
                        onClick = { mode = item },
                        modifier = Modifier.weight(1f)
                    ) { Text(item.title) }
                } else {
                    OutlinedButton(
                        onClick = { mode = item },
                        modifier = Modifier.weight(1f)
                    ) { Text(item.title) }
                }
            }
        }
        Text(
            text = "${mode.comment} (${mode.tariff.fmt(2)} грн/кВт*год)",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                // Перетворення тексту в числа. Кома замінюється на крапку,
                // щоб працювало і при "0,95".
                val u = voltage.replace(',', '.').toDoubleOrNull()
                val i = current.replace(',', '.').toDoubleOrNull()
                val cos = powerFactor.replace(',', '.').toDoubleOrNull()
                val t = hours.replace(',', '.').toDoubleOrNull()

                if (u == null || i == null || cos == null || t == null) {
                    error = "Усі поля мають містити числа"
                    result = null
                } else if (cos !in 0.0..1.0) {
                    error = "cos(fi) має бути в межах від 0 до 1"
                    result = null
                } else {
                    error = null
                    val reading = MeterReading(u, i, cos)
                    val p = EnergyCalculator.activePowerKw(reading)
                    val w = EnergyCalculator.energyKwh(p, t)
                    result = CalculationResult(
                        activePowerKw = p,
                        apparentPowerKva = EnergyCalculator.apparentPowerKva(reading),
                        reactivePowerKvar = EnergyCalculator.reactivePowerKvar(reading),
                        energyKwh = w,
                        costUah = EnergyCalculator.cost(w, mode.tariff)
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Розрахувати споживання")
        }

        error?.let { message ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }

        result?.let { r ->
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    ResultRow("Активна потужність P", "${r.activePowerKw.fmt(3)} кВт")
                    ResultRow("Повна потужність S", "${r.apparentPowerKva.fmt(3)} кВ*А")
                    ResultRow("Реактивна потужність Q", "${r.reactivePowerKvar.fmt(3)} квар")
                    ResultRow("Спожита енергія W", "${r.energyKwh.fmt(2)} кВт*год")
                    ResultRow("Вартість", "${r.costUah.fmt(2)} грн")
                    ResultRow(
                        "Рівень навантаження",
                        EnergyCalculator.loadLevel(r.activePowerKw).title
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Добовий профіль споживання",
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Графік побудовано бібліотекою MPAndroidChart",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = { profile = ConsumptionSimulator.dailyProfile(mode = mode) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (profile.isEmpty()) "Завантажити профіль за добу" else "Оновити профіль")
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (profile.isNotEmpty()) {
            ConsumptionChart(profile = profile)

            Spacer(modifier = Modifier.height(12.dp))

            val peak = EnergyCalculator.peakPoint(profile)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    ResultRow(
                        "Спожито за добу",
                        "${EnergyCalculator.dailyEnergyKwh(profile).fmt(1)} кВт*год"
                    )
                    ResultRow(
                        "Середня потужність",
                        "${EnergyCalculator.averagePowerKw(profile).fmt(2)} кВт"
                    )
                    ResultRow(
                        "Максимум",
                        peak?.let { "${it.powerKw.fmt(2)} кВт о ${it.hour.asHourLabel()}" } ?: "-"
                    )
                    ResultRow(
                        "Коефіцієнт заповнення",
                        EnergyCalculator.loadFactor(profile).fmt(2)
                    )
                    ResultRow(
                        "Прогноз на наступну годину",
                        "${EnergyCalculator.forecastNextHourKw(profile).fmt(2)} кВт"
                    )
                }
            }
        }
    }
}

@Composable
private fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier
    )
}

/** Один рядок таблиці результатів: підпис ліворуч, значення праворуч. */
@Composable
private fun ResultRow(title: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
        Text(text = value, style = MaterialTheme.typography.titleMedium)
    }
}
