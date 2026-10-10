package app.energymonitor.ui.reading

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.energymonitor.domain.fmt
import app.energymonitor.ui.nav.ScreenHeader
import app.energymonitor.viewmodel.ReadingsViewModel
import app.energymonitor.viewmodel.SortOrder

/**
 * Composable-екран відображення списку збережених показників.
 * Працює як спостерігач за колекцією даних у ViewModel.
 */
@Composable
fun ReadingsListScreen(
    viewModel: ReadingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Спостереження за даними: список автоматично оновиться, коли ViewModel змінить state
    val readings by viewModel.readings.collectAsState()
    val filterQuery by viewModel.filterQuery.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()

    var forecastKw by remember { mutableStateOf<Double?>(null) }
    LaunchedEffect(readings) {
        forecastKw = viewModel.forecastKw()
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        ScreenHeader(title = "Історія показників", onBack = onBack)
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = filterQuery,
            onValueChange = viewModel::setFilterQuery,
            label = { Text("Пошук за назвою точки обліку") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SortOrder.values().forEach { order ->
                val label = if (order == sortOrder) "✓ ${order.title}" else order.title
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (order == sortOrder) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable { viewModel.setSortOrder(order) }
                )
            }
        }

        forecastKw?.let { forecast ->
            Spacer(modifier = Modifier.height(8.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Прогноз на наступну годину: ${forecast.fmt(2)} кВт",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (readings.isEmpty()) {
            Text(
                text = "Показників ще немає — додайте перший на попередньому екрані.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn {
                items(readings, key = { it.id }) { record ->
                    ReadingRow(record = record)
                }
            }
        }
    }
}
