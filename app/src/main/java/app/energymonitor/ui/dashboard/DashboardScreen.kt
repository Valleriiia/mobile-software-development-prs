package app.energymonitor.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.energymonitor.domain.ConsumptionSimulator
import app.energymonitor.domain.EnergyCalculator
import app.energymonitor.domain.LoadLevel
import app.energymonitor.domain.OperationMode
import app.energymonitor.domain.asHourLabel
import app.energymonitor.domain.fmt
import app.energymonitor.ui.theme.LoadNormal
import app.energymonitor.ui.theme.LoadOverload
import app.energymonitor.ui.theme.LoadWarning

@Composable
fun DashboardScreen(modifier: Modifier = Modifier) {

    var mode by remember { mutableStateOf(OperationMode.DAY) }
    // refreshKey не зберігає жодних даних — це лише "тригер": збільшення
    // цього числа примушує remember(mode, refreshKey) нижче перерахувати дані.
    var refreshKey by remember { mutableStateOf(0) }

    val profile = remember(mode, refreshKey) {
        ConsumptionSimulator.dailyProfile(mode = mode)
    }
    val devices = remember(mode, refreshKey) {
        ConsumptionSimulator.devices(mode)
    }

    val totalKw = devices.filter { it.isOn }.sumOf { it.powerKw }
    val level = EnergyCalculator.loadLevel(totalKw)
    val levelColor = when (level) {
        LoadLevel.NORMAL -> LoadNormal
        LoadLevel.WARNING -> LoadWarning
        LoadLevel.OVERLOAD -> LoadOverload
    }
    val dailyKwh = EnergyCalculator.dailyEnergyKwh(profile)
    val peak = EnergyCalculator.peakPoint(profile)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        DashboardHeader(
            title = "Панель моніторингу",
            subtitle = "Режим: ${mode.title}. ${mode.comment}"
        )

        Spacer(modifier = Modifier.height(16.dp))

        ModeSelector(
            selected = mode,
            onModeSelected = { mode = it }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // BoxWithConstraints дає доступ до maxWidth — ширини, реально доступної
        // цьому елементу; за порогом 600.dp обирається компонування карток.
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val isWideScreen = maxWidth > 600.dp

            if (isWideScreen) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MetricCard(
                        title = "Поточне навантаження",
                        value = "${totalKw.fmt(2)} кВт",
                        accent = levelColor,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "Спожито за добу",
                        value = "${dailyKwh.fmt(1)} кВт*год",
                        accent = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "Вартість за добу",
                        value = "${EnergyCalculator.cost(dailyKwh, mode.tariff).fmt(2)} грн",
                        accent = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        MetricCard(
                            title = "Навантаження",
                            value = "${totalKw.fmt(2)} кВт",
                            accent = levelColor,
                            modifier = Modifier.weight(1f)
                        )
                        MetricCard(
                            title = "За добу",
                            value = "${dailyKwh.fmt(1)} кВт*год",
                            accent = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    MetricCard(
                        title = "Вартість за добу (тариф ${mode.tariff.fmt(2)} грн)",
                        value = "${EnergyCalculator.cost(dailyKwh, mode.tariff).fmt(2)} грн",
                        accent = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Стан системи: ${level.title}",
                    style = MaterialTheme.typography.titleMedium,
                    color = levelColor
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Договірна потужність: " +
                        "${EnergyCalculator.CONTRACT_POWER_KW.fmt(1)} кВт",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = peak?.let {
                        "Максимум доби: ${it.powerKw.fmt(2)} кВт о ${it.hour.asHourLabel()}"
                    } ?: "Дані профілю відсутні",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Прогноз на наступну годину: " +
                        "${EnergyCalculator.forecastNextHourKw(profile).fmt(2)} кВт",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Споживачі об'єкта",
            style = MaterialTheme.typography.titleLarge
        )
        // Розділювач через Box замість Divider, щоб не залежати від
        // конкретної версії Material3, де сигнатура Divider часто змінюється.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )

        devices.forEach { device ->
            DeviceRow(device = device, totalKw = totalKw)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { refreshKey++ },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Оновити дані моніторингу")
        }
    }
}
