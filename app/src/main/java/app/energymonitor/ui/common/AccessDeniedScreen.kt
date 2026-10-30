package app.energymonitor.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.energymonitor.ui.nav.ScreenHeader

/**
 * Показується, коли роль користувача не дозволяє відкрити екран. Це друга лінія
 * захисту: кнопку в меню для такої ролі вже приховано, а сервер у будь-якому разі
 * відхиляє заборонені операції відповіддю 403.
 */
@Composable
fun AccessDeniedScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        ScreenHeader(title = "Доступ обмежено", onBack = onBack)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Ця дія доступна лише адміністратору. Увійдіть під відповідним обліковим записом.",
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
