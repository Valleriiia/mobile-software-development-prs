package app.energymonitor.ui.reading

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.energymonitor.domain.LoadLevel
import app.energymonitor.domain.ReadingRecord
import app.energymonitor.domain.fmt
import app.energymonitor.ui.nav.ScreenHeader
import app.energymonitor.viewmodel.DataSourceMode
import app.energymonitor.viewmodel.ReadingsViewModel
import app.energymonitor.viewmodel.SortOrder
import app.energymonitor.viewmodel.UiState

/**
 * Екран історії показників: перегляд, пошук, фільтр за рівнем навантаження,
 * сортування, вибір джерела даних та редагування/видалення записів.
 * Екран лише відображає стан ViewModel і передає їй дії користувача.
 */
@Composable
fun ReadingsListScreen(
    viewModel: ReadingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    canEdit: Boolean = true
) {
    val readings by viewModel.readings.collectAsState()
    val stats by viewModel.stats.collectAsState()
    val filterQuery by viewModel.filterQuery.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
    val levelFilter by viewModel.levelFilter.collectAsState()
    val sourceMode by viewModel.sourceMode.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    var forecastKw by remember { mutableStateOf<Double?>(null) }
    var editing by remember { mutableStateOf<ReadingRecord?>(null) }

    LaunchedEffect(readings) { forecastKw = viewModel.forecastKw() }
    // Успішне збереження закриває діалог; помилка лишає його відкритим із текстом.
    LaunchedEffect(uiState) { if (uiState is UiState.Success) editing = null }

    val levelOptions: List<LoadLevel?> = listOf<LoadLevel?>(null) + LoadLevel.values().toList()

    // Увесь екран — один LazyColumn, щоб елементи керування прокручувалися разом зі списком.
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item { ScreenHeader(title = "Історія показників", onBack = onBack) }

        item {
            ChipRow(
                options = DataSourceMode.values().toList(),
                selected = sourceMode,
                label = { it.title },
                onSelect = viewModel::setSourceMode
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isRefreshing) "Оновлення…" else statusMessage ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { viewModel.refresh(force = true) }) { Text("Оновити") }
            }
        }

        item {
            OutlinedTextField(
                value = filterQuery,
                onValueChange = viewModel::setFilterQuery,
                label = { Text("Пошук за назвою точки обліку") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

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
                options = SortOrder.values().toList(),
                selected = sortOrder,
                label = { it.title },
                onSelect = viewModel::setSortOrder
            )
        }

        item { StatsPanel(stats = stats) }

        forecastKw?.let { forecast ->
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Прогноз на наступну годину: ${forecast.fmt(2)} кВт",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        if (editing == null && uiState is UiState.Error) {
            item { FormMessage(message = (uiState as UiState.Error).message, isError = true) }
        }

        if (readings.isEmpty()) {
            item {
                Text(
                    text = "Записів за вибраними умовами немає.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(readings, key = { it.id }) { record ->
                val edit: () -> Unit = {
                    viewModel.resetUiState()
                    editing = record
                }
                val delete: () -> Unit = { viewModel.deleteReading(record.id) }
                ReadingRow(
                    record = record,
                    onEdit = if (canEdit) edit else null,
                    onDelete = if (canEdit) delete else null
                )
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }

    editing?.let { record ->
        EditReadingDialog(
            record = record,
            errorMessage = (uiState as? UiState.Error)?.message,
            onDismiss = { editing = null },
            onConfirm = { voltage, current, powerFactor ->
                viewModel.updateReading(record, voltage, current, powerFactor)
            }
        )
    }
}
