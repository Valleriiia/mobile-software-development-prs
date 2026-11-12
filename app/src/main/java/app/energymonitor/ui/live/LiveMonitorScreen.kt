package app.energymonitor.ui.live

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.energymonitor.data.live.SensorFault
import app.energymonitor.domain.EnergyCalculator
import app.energymonitor.domain.LoadLevel
import app.energymonitor.domain.fmt
import app.energymonitor.ui.nav.ScreenHeader
import app.energymonitor.ui.reading.ChipRow
import app.energymonitor.ui.reading.StatCard
import app.energymonitor.ui.theme.LoadNormal
import app.energymonitor.ui.theme.LoadOverload
import app.energymonitor.ui.theme.LoadWarning
import app.energymonitor.viewmodel.LiveConnection
import app.energymonitor.viewmodel.LiveMonitorViewModel

/**
 * Екран живого моніторингу. Усю роботу з потоками виконує ViewModel, а екран
 * лише збирає готовий StateFlow через collectAsState і малює його. Коли екран
 * закрито або застосунок згорнуто, підписка припиняється, і датчики зупиняються.
 */
@Composable
fun LiveMonitorScreen(
    viewModel: LiveMonitorViewModel,
    canSimulateFaults: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    val fault by viewModel.fault.collectAsState()
    val sample = state.sample

    val levelColor = when (sample?.level) {
        LoadLevel.NORMAL -> LoadNormal
        LoadLevel.WARNING -> LoadWarning
        LoadLevel.OVERLOAD -> LoadOverload
        null -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val connectionColor = when (state.connection) {
        LiveConnection.LIVE -> LoadNormal
        LiveConnection.RETRYING -> LoadWarning
        LiveConnection.FAILED -> LoadOverload
        LiveConnection.CONNECTING -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ScreenHeader(title = "Моніторинг наживо", onBack = onBack)

        // Індикатор зв'язку: колір і текст змінюються при повторних спробах та остаточному збої
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (state.running) connectionColor else MaterialTheme.colorScheme.outline)
            )
            Text(
                text = if (state.running) state.statusText else "Призупинено: датчики зупиняться через 5 с",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Активна потужність",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = sample?.let { "${it.powerKw.fmt(2)} кВт" } ?: "—",
                    style = MaterialTheme.typography.displaySmall,
                    color = levelColor
                )
                Text(
                    text = sample?.let {
                        "${it.level.title} · відхилення від прогнозу ${it.deviationPercent.fmt(1)}%"
                    } ?: "Очікуємо перше показання",
                    style = MaterialTheme.typography.bodyMedium,
                    color = levelColor
                )
            }
        }

        val tiles = listOf(
            "Напруга" to (sample?.let { "${it.voltage.fmt(1)} В" } ?: "—"),
            "Струм" to (sample?.let { "${it.current.fmt(1)} А" } ?: "—"),
            "cos(fi)" to (sample?.let { it.powerFactor.fmt(2) } ?: "—"),
            "Прогноз тренду" to (sample?.let { "${it.trendForecastKw.fmt(2)} кВт" } ?: "—"),
            "Прогноз за історією" to (sample?.baselineKw?.let { "${it.fmt(2)} кВт" } ?: "—"),
            "Договірна межа" to "${EnergyCalculator.CONTRACT_POWER_KW.fmt(1)} кВт"
        )
        tiles.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { (title, value) -> StatCard(title, value, Modifier.weight(1f)) }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Потужність за останні вимірювання", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))
                LivePowerChart(
                    power = state.powerHistory,
                    forecast = state.forecastHistory,
                    limitKw = EnergyCalculator.CONTRACT_POWER_KW,
                    powerColor = MaterialTheme.colorScheme.primary,
                    forecastColor = MaterialTheme.colorScheme.tertiary
                )
                Text(
                    text = "суцільна: факт · пунктир: прогноз · червона лінія: договірна потужність",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = viewModel::togglePause, modifier = Modifier.weight(1f)) {
                Text(if (state.running) "Пауза" else "Продовжити")
            }
            if (state.connection == LiveConnection.FAILED) {
                OutlinedButton(onClick = viewModel::restart, modifier = Modifier.weight(1f)) {
                    Text("Перезапустити")
                }
            }
        }

        if (canSimulateFaults) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Імітація збою датчика струму", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Тимчасовий: два збої підряд, потім успіх (повтори з паузою 0,5 і 1 с). " +
                            "Постійний: після 4 спроб потік зупиняється.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    ChipRow(
                        options = SensorFault.values().toList(),
                        selected = fault,
                        label = { it.title },
                        onSelect = viewModel::setFault
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}
