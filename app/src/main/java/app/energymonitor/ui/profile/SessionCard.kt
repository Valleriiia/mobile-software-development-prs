package app.energymonitor.ui.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.energymonitor.data.auth.Session
import kotlinx.coroutines.delay
import java.util.Locale

/** Поточний користувач, його роль і скільки ще діє токен (лічильник оновлюється щосекунди). */
@Composable
fun SessionCard(session: Session, onLogout: () -> Unit, modifier: Modifier = Modifier) {
    val remainingMillis by produceState(
        initialValue = session.expiresAtMillis - System.currentTimeMillis(),
        key1 = session
    ) {
        while (true) {
            value = session.expiresAtMillis - System.currentTimeMillis()
            delay(1000)
        }
    }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Обліковий запис", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text("Користувач: ${session.name} (${session.login})", style = MaterialTheme.typography.bodyMedium)
            Text("Роль: ${session.role.title}", style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "Токен діє ще: ${formatRemaining(remainingMillis)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onLogout, modifier = Modifier.fillMaxWidth()) { Text("Вийти") }
        }
    }
}

private fun formatRemaining(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    return String.format(Locale.US, "%02d:%02d", totalSeconds / 60, totalSeconds % 60)
}
