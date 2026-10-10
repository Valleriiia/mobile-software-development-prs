package app.energymonitor.ui.menu

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.energymonitor.domain.ConsumptionSimulator
import app.energymonitor.ui.nav.Routes

/**
 * Головне меню: стартовий екран навігаційного графа. Складається з картки
 * стану системи і кнопок переходу на інші екрани. Сам екран нічого не знає
 * про NavController: він лише викликає onNavigate(route), а що саме
 * відбудеться, вирішує граф у MainActivity.
 *
 * @param onNavigate callback, якому передається маршрут з Routes
 */
@Composable
fun MainMenuScreen(
    onNavigate: (route: String) -> Unit,
    modifier: Modifier = Modifier
) {
    // remember без ключів: показання генерується один раз за відкриття екрана,
    // а не при кожній рекомпозиції
    val reading = remember { ConsumptionSimulator.randomReading() }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // На планшетах (ширше 600dp) обмежуємо ширину контенту,
        // щоб кнопки не розтягувалися на весь екран
        val contentWidth = if (maxWidth > 600.dp) 480.dp else maxWidth

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(modifier = Modifier.widthIn(max = contentWidth)) {
                Text(
                    text = "Система моніторингу енергоспоживання",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))

                SystemStatusCard(reading = reading)

                Spacer(modifier = Modifier.height(24.dp))

                MenuButton("Моніторинг", onClick = { onNavigate(Routes.MONITOR) })
                MenuButton("Розрахунок", onClick = { onNavigate(Routes.CALCULATOR) })
                MenuButton("Панель", onClick = { onNavigate(Routes.DASHBOARD) })
                MenuButton("Додати показник", onClick = { onNavigate(Routes.ADD_READING) })
                MenuButton("Історія показників", onClick = { onNavigate(Routes.READINGS_LIST) })
                MenuButton("Профіль", onClick = { onNavigate(Routes.profile("menu")) })
            }
        }
    }
}