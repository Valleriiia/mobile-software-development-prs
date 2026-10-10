package app.energymonitor.ui.reading

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.energymonitor.ui.nav.ScreenHeader
import app.energymonitor.viewmodel.ReadingsViewModel

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

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        ScreenHeader(title = "Історія показників", onBack = onBack)
        Spacer(modifier = Modifier.height(12.dp))
        if (readings.isEmpty()) {
            Text("Показників ще немає.", style = MaterialTheme.typography.bodyMedium)
        } else {
            LazyColumn {
                items(readings, key = { it.id }) { record -> ReadingRow(record = record) }
            }
        }
    }
}
