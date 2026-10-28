package app.energymonitor.ui.reading

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.energymonitor.domain.LoadLevel
import app.energymonitor.domain.ReadingAnalytics
import app.energymonitor.domain.ReadingRecord
import app.energymonitor.domain.asDateTimeLabel
import app.energymonitor.domain.fmt
import app.energymonitor.ui.theme.LoadNormal
import app.energymonitor.ui.theme.LoadOverload
import app.energymonitor.ui.theme.LoadWarning

/**
 * Stateless-компонент (View): пасивно відображає запис і рівень навантаження.
 * Кнопки "Змінити"/"Видалити" з'являються лише тоді, коли передано відповідні
 * обробники, тож екран сам вирішує, чи доступне редагування.
 */
@Composable
fun ReadingRow(
    record: ReadingRecord,
    modifier: Modifier = Modifier,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    val level = ReadingAnalytics.levelOf(record)
    val levelColor = when (level) {
        LoadLevel.NORMAL -> LoadNormal
        LoadLevel.WARNING -> LoadWarning
        LoadLevel.OVERLOAD -> LoadOverload
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(record.label, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = record.timestampMillis.asDateTimeLabel(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${record.activePowerKw.fmt(2)} кВт",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = level.title,
                        style = MaterialTheme.typography.bodySmall,
                        color = levelColor
                    )
                }
            }
            if (onEdit != null || onDelete != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    if (onEdit != null) TextButton(onClick = onEdit) { Text("Змінити") }
                    if (onDelete != null) TextButton(onClick = onDelete) { Text("Видалити") }
                }
            }
        }
    }
}
