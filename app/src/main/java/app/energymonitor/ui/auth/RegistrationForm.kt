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
import app.energymonitor.domain.AuthValidator

// Підсвічування помилок окремих полів виконується "на льоту" через
// AuthValidator, а остаточна перевірка — у батьківському екрані (AuthScreen).
@Composable
fun RegistrationForm(
    onRegister: (name: String, email: String, password: String, confirm: String) -> Unit,
    onSwitchToLogin: () -> Unit,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    val canSubmit = name.isNotBlank() && email.isNotBlank() &&
        password.isNotBlank() && confirmPassword.isNotBlank() && !isLoading

    Column(modifier = modifier.fillMaxWidth()) {
        AuthTextField(
            value = name,
            onValueChange = { name = it },
            label = "Ім'я та прізвище",
            isError = name.isNotEmpty() && name.trim().length < 2
        )
        Spacer(modifier = Modifier.height(12.dp))

        AuthTextField(
            value = email,
            onValueChange = { email = it },
            label = "Електронна пошта",
            keyboardType = KeyboardType.Email,
            isError = email.isNotEmpty() && !AuthValidator.isEmailValid(email)
        )
        Spacer(modifier = Modifier.height(12.dp))

        AuthTextField(
            value = password,
            onValueChange = { password = it },
            label = "Пароль (літери та цифри, мін. 6)",
            isPassword = true,
            keyboardType = KeyboardType.Password,
            isError = password.isNotEmpty() && !AuthValidator.isPasswordStrong(password)
        )
        Spacer(modifier = Modifier.height(12.dp))

        AuthTextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it },
            label = "Підтвердження пароля",
            isPassword = true,
            keyboardType = KeyboardType.Password,
            isError = confirmPassword.isNotEmpty() && confirmPassword != password
        )
        Spacer(modifier = Modifier.height(20.dp))

        AuthPrimaryButton(
            text = if (isLoading) "Створення запису..." else "Зареєструватися",
            onClick = { onRegister(name, email, password, confirmPassword) },
            enabled = canSubmit
        )

        AuthSwitchButton(
            text = "Вже маю обліковий запис. Увійти",
            onClick = onSwitchToLogin,
            enabled = !isLoading
        )
    }
}
