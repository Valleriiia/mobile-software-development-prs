package app.energymonitor.ui.reading

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.energymonitor.domain.ReadingStats
import app.energymonitor.domain.fmt

/**
 * Зведена статистика за поточним (відфільтрованим) списком. Розташування
 * адаптивне: на широкому екрані чотири картки в ряд, на вузькому — по дві.
 */
@Composable
fun StatsPanel(stats: ReadingStats, modifier: Modifier = Modifier) {
    val cards = listOf(
        "Записів" to stats.count.toString(),
        "Середня потужність" to "${stats.averagePowerKw.fmt(2)} кВт",
        "Максимум" to (stats.peakLabel?.let { "${stats.peakPowerKw.fmt(2)} кВт · $it" } ?: "—"),
        "Перевантажень" to stats.overloadCount.toString()
    )

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        if (maxWidth > 600.dp) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                cards.forEach { (title, value) -> StatCard(title, value, Modifier.weight(1f)) }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                cards.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { (title, value) -> StatCard(title, value, Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
fun StatCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(text = value, style = MaterialTheme.typography.titleMedium)
        }
    }
}
