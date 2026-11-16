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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import app.energymonitor.viewmodel.MetersUiState
import app.energymonitor.viewmodel.MetersViewModel

/**
 * Скільки службових елементів іде в LazyColumn перед картками (підсумок і фільтри).
 * Потрібно, щоб за позицією картки знайти її індекс у списку під час прокручування.
 */
private const val LIST_HEADER_ITEMS = 2

/**
 * Точки обліку: мапа і список карток на одному екрані. Дані приходять з двох
 * ендпоінтів REST API та з бази показників; екран лише показує готовий стан
 * ViewModel. Вибір точки в списку наближає мапу до маркера, а клік по маркеру
 * виділяє і прокручує до картки: обидва компоненти читають один selectedId.
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
    val listState = rememberLazyListState()

    // Вибрана точка з'явилася (клік по маркеру): прокручуємо до її картки, якщо її не видно.
    LaunchedEffect(state.selectedId) {
        val index = state.cards.indexOfFirst { it.meter.id == state.selectedId }
        if (index >= 0) listState.scrollToCardIfHidden(LIST_HEADER_ITEMS + index)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        ScreenHeader(title = "Точки обліку", onBack = onBack)
        Spacer(modifier = Modifier.height(8.dp))

        BoxWithConstraints(modifier = Modifier.weight(1f)) {
            val map = @Composable { mapModifier: Modifier ->
                MetersMap(
                    cards = state.cards,
                    selectedId = state.selectedId,
                    onSelect = viewModel::select,
                    modifier = mapModifier
                )
            }
            val list = @Composable { listModifier: Modifier ->
                MetersList(
                    state = state,
                    sort = sort,
                    levelFilter = levelFilter,
                    listState = listState,
                    viewModel = viewModel,
                    modifier = listModifier
                )
            }

            if (maxWidth > maxHeight) {
                // Альбомна орієнтація й планшети: мапа ліворуч, список праворуч
                Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    map(Modifier.weight(1f).fillMaxSize())
                    list(Modifier.weight(1f).fillMaxSize())
                }
            } else {
                // Портретна орієнтація: мапа зверху фіксованої висоти, список займає решту
                Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    map(Modifier.fillMaxWidth().height(260.dp))
                    list(Modifier.weight(1f).fillMaxWidth())
                }
            }
        }
    }
}

private suspend fun LazyListState.scrollToCardIfHidden(targetIndex: Int) {
    val visible = layoutInfo.visibleItemsInfo.any { it.index == targetIndex }
    if (!visible) animateScrollToItem(targetIndex)
}

@Composable
private fun MetersList(
    state: MetersUiState,
    sort: MeterSort,
    levelFilter: LoadLevel?,
    listState: LazyListState,
    viewModel: MetersViewModel,
    modifier: Modifier = Modifier
) {
    val levelOptions: List<LoadLevel?> = listOf<LoadLevel?>(null) + LoadLevel.values().toList()

    LazyColumn(
        state = listState,
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "summary") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                FleetSummaryPanel(summary = state.summary)
            }
        }

        item(key = "filters") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ChipRow(
                    options = levelOptions,
                    selected = levelFilter,
                    label = { it?.title ?: "Усі рівні" },
                    onSelect = viewModel::setLevelFilter
                )
                ChipRow(
                    options = MeterSort.values().toList(),
                    selected = sort,
                    label = { it.title },
                    onSelect = viewModel::setSort
                )
            }
        }

        if (state.cards.isEmpty()) {
            item(key = "empty") {
                Text(
                    text = if (state.isLoading) "" else "Точок обліку за вибраними умовами немає.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            // key = id: Compose відрізняє картки між собою, тож виділення і прокручування
            // не "перескакують" на інші елементи після зміни порядку чи фільтрації.
            items(state.cards, key = { it.meter.id }) { card ->
                val selected = card.meter.id == state.selectedId
                MeterCard(
                    data = card,
                    selected = selected,
                    onClick = { viewModel.select(if (selected) null else card.meter.id) }
                )
            }
        }

        item(key = "footer") { Spacer(modifier = Modifier.height(16.dp)) }
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

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        cards.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { (title, value) -> StatCard(title, value, Modifier.weight(1f)) }
            }
        }
    }
}
