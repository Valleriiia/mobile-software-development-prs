package app.energymonitor.ui.menu

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Пункт головного меню: кнопка на всю ширину. Винесена в окремий composable,
 * щоб усі пункти мали однаковий вигляд, а змінити його можна було в одному місці.
 *
 * @param text напис на кнопці
 * @param onClick дія при натисканні (зазвичай виклик навігації)
 */
@Composable
fun MenuButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(text)
    }
}
