package app.energymonitor.ui.menu

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.energymonitor.domain.EnergyCalculator
import app.energymonitor.domain.LoadLevel
import app.energymonitor.domain.MeterReading
import app.energymonitor.domain.fmt
import app.energymonitor.ui.theme.LoadNormal
import app.energymonitor.ui.theme.LoadOverload
import app.energymonitor.ui.theme.LoadWarning

/**
 * Картка стану системи на головному меню. Інтегрує бізнес-логіку
 * (EnergyCalculator): з одного показання обчислює активну потужність
 * і визначає рівень навантаження: Норма, Підвищене чи Перевантаження.
 *
 * @param reading миттєве показання лічильника (напруга, струм, cos φ)
 */
@Composable
fun SystemStatusCard(
    reading: MeterReading,
    modifier: Modifier = Modifier
) {
    // Розрахунок активної потужності на основі переданих показників
    val powerKw = EnergyCalculator.activePowerKw(reading)

    // Визначення рівня навантаження (норма, попередження, перевантаження)
    val level = EnergyCalculator.loadLevel(powerKw)

    // Той самий "семафор" кольорів, що й на екранах моніторингу та панелі
    val color: Color = when (level) {
        LoadLevel.NORMAL -> LoadNormal
        LoadLevel.WARNING -> LoadWarning
        LoadLevel.OVERLOAD -> LoadOverload
    }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Поточний стан системи", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "${powerKw.fmt(2)} кВт — ${level.title}",
                style = MaterialTheme.typography.headlineMedium,
                color = color
            )
        }
    }
}
