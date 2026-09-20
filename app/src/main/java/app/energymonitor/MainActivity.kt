package app.energymonitor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.energymonitor.ui.ScreenSelector
import app.energymonitor.ui.calculator.CalculatorScreen
import app.energymonitor.ui.monitor.MonitorScreen
import app.energymonitor.ui.theme.EnergyMonitorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EnergyMonitorTheme {
                EnergyMonitorApp()
            }
        }
    }
}

@Composable
fun EnergyMonitorApp() {
    var currentScreen by remember { mutableStateOf(AppScreen.MONITOR) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenSelector(
                selected = currentScreen,
                onSelectedChange = { currentScreen = it }
            )
            when (currentScreen) {
                AppScreen.MONITOR -> MonitorScreen(modifier = Modifier.weight(1f))
                AppScreen.CALCULATOR -> CalculatorScreen(modifier = Modifier.weight(1f))
            }
        }
    }
}