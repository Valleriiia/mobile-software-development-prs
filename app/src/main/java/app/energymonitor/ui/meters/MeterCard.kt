package app.energymonitor.ui.meters

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.energymonitor.domain.LoadLevel
import app.energymonitor.domain.MeterCardData
import app.energymonitor.domain.fmt
import app.energymonitor.ui.theme.LoadNormal
import app.energymonitor.ui.theme.LoadOverload
import app.energymonitor.ui.theme.LoadWarning

/**
 * Картка точки обліку: довідкові дані з REST API (назва, розташування,
 * договірна потужність) разом із розрахунками за останнім показником.
 * Картка лише відображає готові MeterCardData і нічого не обчислює сама.
 */
@Composable
fun MeterCard(data: MeterCardData, modifier: Modifier = Modifier) {
    val levelColor = when (data.level) {
        LoadLevel.NORMAL -> LoadNormal
        LoadLevel.WARNING -> LoadWarning
        LoadLevel.OVERLOAD -> LoadOverload
        null -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(data.meter.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "${data.meter.building}, поверх ${data.meter.floor}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = data.level?.title ?: "Немає даних",
                    style = MaterialTheme.typography.labelLarge,
                    color = levelColor
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            val power = data.powerKw
            val percent = data.loadPercent
            if (power == null || percent == null) {
                Text(
                    text = "Показників для цієї точки ще немає",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "Навантаження: ${power.fmt(2)} із ${data.meter.contractPowerKw.fmt(1)} кВт (${percent.fmt(0)}%)",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                LoadBar(fraction = (percent / 100.0).coerceIn(0.0, 1.0).toFloat(), color = levelColor)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "За добу: ${(data.dailyEnergyKwh ?: 0.0).fmt(1)} кВт·год, ≈ ${(data.dailyCostUah ?: 0.0).fmt(0)} грн",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun LoadBar(fraction: Float, color: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color)
        )
    }
}
