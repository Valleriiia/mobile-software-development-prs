package app.energymonitor.ui.errorlog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.energymonitor.data.error.ErrorLogger
import app.energymonitor.ui.nav.ScreenHeader
import app.energymonitor.ui.theme.LoadWarning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Журнал мережевих збоїв (повтори запитів, остаточні помилки, відсутність мережі).
 * Файл читається поза головним потоком, щоб екран не підвисав на великому журналі.
 */
@Composable
fun ErrorLogScreen(
    logger: ErrorLogger,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var lines by remember { mutableStateOf<List<String>>(emptyList()) }
    var reloadKey by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(reloadKey) {
        lines = withContext(Dispatchers.IO) { logger.readAll() }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        item { ScreenHeader(title = "Журнал помилок", onBack = onBack) }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { reloadKey++ }) { Text("Оновити") }
                TextButton(onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) { logger.clear() }
                        reloadKey++
                    }
                }) { Text("Очистити") }
            }
        }

        if (lines.isEmpty()) {
            item {
                Text(
                    text = "Журнал порожній. Мережевих збоїв не зафіксовано.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(lines) { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = when {
                        "[ERROR]" in line -> MaterialTheme.colorScheme.error
                        "[WARN]" in line -> LoadWarning
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
            }
        }
    }
}
