package app.energymonitor.ui.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import app.energymonitor.data.remote.FailureMode
import app.energymonitor.data.remote.MockBackend
import app.energymonitor.ui.reading.ChipRow

/**
 * Панель розробника: перемикає режим збоїв мок-сервера, щоб на живому застосунку
 * можна було побачити повторні спроби, офлайн-режим і повідомлення про помилки.
 */
@Composable
fun DevToolsCard(backend: MockBackend, modifier: Modifier = Modifier) {
    var mode by remember { mutableStateOf(backend.failureMode) }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Мок-сервер: режим збоїв", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Нестабільний: два збої підряд, потім успіх (працюють повтори запитів)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            ChipRow(
                options = FailureMode.values().toList(),
                selected = mode,
                label = { it.title },
                onSelect = {
                    mode = it
                    backend.failureMode = it
                }
            )
        }
    }
}
