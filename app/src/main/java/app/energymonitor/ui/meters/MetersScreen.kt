package app.energymonitor.ui.meters

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.energymonitor.domain.FleetSummary
import app.energymonitor.domain.LoadLevel
import app.energymonitor.domain.fmt
import app.energymonitor.ui.nav.ScreenHeader
import app.energymonitor.ui.reading.ChipRow
import app.energymonitor.ui.reading.StatCard
import app.energymonitor.viewmodel.MeterSort
import app.energymonitor.viewmodel.MetersViewModel

/**
 * Точки обліку з REST API у вигляді карток. Дані приходять з двох ендпоінтів
 * (довідник і тарифи) та з бази показників; екран лише показує готовий стан
 * ViewModel: підсумок, фільтр за рівнем навантаження, сортування й картки.
 */
@Composable
fun MetersScreen(
    viewModel: MetersViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    val sort by viewModel.sort.collectAsState()
    val levelFilter by viewModel.levelFilter.collectAsState()

    val levelOptions: List<LoadLevel?> = listOf<LoadLevel?>(null) + LoadLevel.values().toList()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item { ScreenHeader(title = "Точки обліку", onBack = onBack) }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (state.isLoading) "Завантаження…" else state.message ?: "Дані з сервера",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { viewModel.load(force = true) }) { Text("Оновити") }
            }
        }

        item { FleetSummaryPanel(summary = state.summary) }

        item {
            ChipRow(
                options = levelOptions,
                selected = levelFilter,
                label = { it?.title ?: "Усі рівні" },
                onSelect = viewModel::setLevelFilter
            )
        }

        item {
            ChipRow(
                options = MeterSort.values().toList(),
                selected = sort,
                label = { it.title },
                onSelect = viewModel::setSort
            )
        }

        if (state.cards.isEmpty()) {
            item {
                Text(
                    text = if (state.isLoading) "" else "Точок обліку за вибраними умовами немає.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(state.cards, key = { it.meter.id }) { card -> MeterCard(data = card) }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

/** Підсумок по всьому об'єкту; розташування карток адаптується до ширини екрана. */
@Composable
private fun FleetSummaryPanel(summary: FleetSummary, modifier: Modifier = Modifier) {
    val cards = listOf(
        "Точок обліку" to summary.meterCount.toString(),
        "Потужність" to "${summary.totalPowerKw.fmt(1)} із ${summary.totalContractKw.fmt(1)} кВт",
        "Завантаження" to "${summary.utilizationPercent.fmt(0)}%",
        "Вартість доби" to "≈ ${summary.totalDailyCostUah.fmt(0)} грн"
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
