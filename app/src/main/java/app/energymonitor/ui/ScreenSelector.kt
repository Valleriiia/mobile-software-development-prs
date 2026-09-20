package app.energymonitor.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.energymonitor.AppScreen

@Composable
fun ScreenSelector(
    selected: AppScreen,
    onSelectedChange: (AppScreen) -> Unit,
    modifier: Modifier = Modifier
) {
    TabRow(
        selectedTabIndex = selected.ordinal,
        modifier = modifier.fillMaxWidth()
    ) {
        AppScreen.values().forEach { screen ->
            Tab(
                selected = screen == selected,
                onClick = { onSelectedChange(screen) },
                text = { Text(text = screen.title) }
            )
        }
    }
}
