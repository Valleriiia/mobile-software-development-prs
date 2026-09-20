package app.energymonitor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import app.energymonitor.ui.monitor.MonitorScreen
import app.energymonitor.ui.theme.EnergyMonitorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EnergyMonitorTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MonitorScreen()
                }
            }
        }
    }
}