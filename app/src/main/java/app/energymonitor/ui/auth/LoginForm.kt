package app.energymonitor.ui.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

// Форма НЕ виконує перевірку сама: вона лише збирає дані та передає їх
// назовні через onLogin, тому ту саму форму можна під'єднати до будь-якої
// логіки перевірки (локальної чи запиту до сервера).
@Composable
fun LoginForm(
    onLogin: (login: String, password: String) -> Unit,
    onSwitchToRegister: () -> Unit,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    var login by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    val canSubmit = login.isNotBlank() && password.isNotBlank() && !isLoading

    Column(modifier = modifier.fillMaxWidth()) {
        AuthTextField(
            value = login,
            onValueChange = { login = it },
            label = "Логін оператора",
            isError = login.isNotEmpty() && login.trim().length < 3
        )
        Spacer(modifier = Modifier.height(12.dp))

        AuthTextField(
            value = password,
            onValueChange = { password = it },
            label = "Пароль",
            isPassword = true,
            keyboardType = KeyboardType.Password,
            isError = password.isNotEmpty() && password.length < 6
        )
        Spacer(modifier = Modifier.height(20.dp))

        AuthPrimaryButton(
            text = if (isLoading) "Перевірка..." else "Увійти в систему",
            onClick = { onLogin(login, password) },
            enabled = canSubmit
        )

        AuthSwitchButton(
            text = "Немає облікового запису? Зареєструватися",
            onClick = onSwitchToRegister,
            enabled = !isLoading
        )
    }
}
