package app.energymonitor.ui.monitor

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.energymonitor.domain.fmt
import app.energymonitor.ui.theme.LoadNormal
import app.energymonitor.ui.theme.LoadOverload
import app.energymonitor.ui.theme.LoadWarning
import kotlin.random.Random

@Composable
fun MonitorScreen(modifier: Modifier = Modifier) {

    var powerKw by remember { mutableStateOf(3.40) }
    var energyKwh by remember { mutableStateOf(42.80) }
    var updateCount by remember { mutableStateOf(0) }

    val tariff = 4.32
    val costUah = energyKwh * tariff

    // animateFloatAsState плавно "переводить" число зі старого значення в нове
    // при кожній зміні стану — саме це дає ефект анімованого лічильника.
    val animatedPower by animateFloatAsState(
        targetValue = powerKw.toFloat(),
        animationSpec = tween(durationMillis = 900),
        label = "power"
    )
    val animatedEnergy by animateFloatAsState(
        targetValue = energyKwh.toFloat(),
        animationSpec = tween(durationMillis = 900),
        label = "energy"
    )
    val animatedCost by animateFloatAsState(
        targetValue = costUah.toFloat(),
        animationSpec = tween(durationMillis = 900),
        label = "cost"
    )
    val statusColor by animateColorAsState(
        targetValue = when {
            powerKw >= 6.0 -> LoadOverload
            powerKw >= 4.5 -> LoadWarning
            else -> LoadNormal
        },
        animationSpec = tween(durationMillis = 900),
        label = "status"
    )
    val statusText = when {
        powerKw >= 6.0 -> "Перевантаження мережі"
        powerKw >= 4.5 -> "Підвищене навантаження"
        else -> "Навантаження в нормі"
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Text(
            text = "Система моніторингу енергоспоживання",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Об'єкт: навчальний корпус №18",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(20.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Поточна потужність",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${animatedPower.fmt(2)} кВт",
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.titleMedium,
                    color = statusColor
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricBox(
                title = "Спожито за добу",
                value = "${animatedEnergy.fmt(1)} кВт*год",
                modifier = Modifier.weight(1f)
            )
            MetricBox(
                title = "Вартість, тариф ${tariff.fmt(2)}",
                value = "${animatedCost.fmt(2)} грн",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                powerKw = Random.nextDouble(1.20, 7.40)
                energyKwh += Random.nextDouble(0.8, 3.5)
                updateCount++
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Оновити показники лічильника")
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Кількість опитувань лічильника: $updateCount",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MetricBox(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}
