package app.energymonitor.ui.profile

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.energymonitor.data.auth.Session
import app.energymonitor.data.remote.MockAuth
import app.energymonitor.data.remote.MockBackend
import app.energymonitor.domain.Permissions
import app.energymonitor.ui.nav.ScreenHeader

/**
 * Екран профілю оператора. Демонструє два механізми:
 *  1) передачу даних між екранами: fromScreen приходить як аргумент
 *     маршруту "profile/{fromScreen}" через NavController;
 *  2) збережене налаштування: перемикач notifyOnOverload, стан якого
 *     підняте на рівень навігаційного графа, тому не губиться при
 *     поверненні на екран.
 *
 * @param fromScreen назва екрана, з якого відкрито профіль
 * @param notifyOnOverload чи ввімкнено сповіщення про перевантаження
 * @param onToggleNotify callback зміни налаштування
 * @param session поточний користувач (роль визначає, чи видно панель розробника)
 * @param backend мок-сервер, режим збоїв якого перемикається в панелі розробника
 * @param mockAuth мок-авторизація, термін дії токенів якої налаштовується там само
 * @param onLogout вихід із облікового запису
 * @param onBack повернення на попередній екран
 */
@Composable
fun ProfileScreen(
    fromScreen: String,
    notifyOnOverload: Boolean,
    onToggleNotify: (Boolean) -> Unit,
    session: Session?,
    backend: MockBackend,
    mockAuth: MockAuth,
    onLogout: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        ScreenHeader(title = "Профіль", onBack = onBack)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Ви перейшли сюди з екрана: $fromScreen",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (session != null) {
            SessionCard(session = session, onLogout = onLogout)
            Spacer(modifier = Modifier.height(16.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Сповіщати про перевантаження", style = MaterialTheme.typography.bodyLarge)
            Switch(checked = notifyOnOverload, onCheckedChange = onToggleNotify)
        }

        if (Permissions.canUseDevTools(session?.role)) {
            Spacer(modifier = Modifier.height(24.dp))
            DevToolsCard(backend = backend, mockAuth = mockAuth)
        }
    }
}
