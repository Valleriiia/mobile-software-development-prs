package app.energymonitor.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import app.energymonitor.domain.AuthValidator
import app.energymonitor.domain.ValidationResult

// Успішний вхід або реєстрація викликають onAuthSuccess() — MainActivity
// реагує на це показом основних екранів застосунку.
@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isRegisterMode by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<ValidationResult?>(null) }

    // rememberCoroutineScope прив'язує корутину до життєвого циклу composable,
    // щоб можна було показати стан завантаження на час "обробки запиту".
    val scope = rememberCoroutineScope()

    BoxWithConstraints(
        modifier = modifier.fillMaxSize()
    ) {
        // На широкому екрані (планшет) форма не розтягується на всю ширину.
        val formWidth = if (maxWidth > 600.dp) 480.dp else maxWidth

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Column(
                modifier = Modifier.widthIn(max = formWidth),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AuthHeader(
                    title = if (isRegisterMode) "Реєстрація" else "Вхід",
                    subtitle = if (isRegisterMode)
                        "Створіть обліковий запис оператора"
                    else
                        "Введіть облікові дані для доступу до показників"
                )

                Spacer(modifier = Modifier.height(24.dp))

                if (isRegisterMode) {
                    RegistrationForm(
                        isLoading = isLoading,
                        onRegister = { name, email, password, confirm ->
                            scope.launch {
                                isLoading = true
                                result = null
                                delay(1200) // імітація запиту до сервера
                                val validation = AuthValidator.validateRegistration(
                                    name, email, password, confirm
                                )
                                result = validation
                                isLoading = false
                                if (validation.isSuccess) {
                                    delay(600) // даємо користувачу побачити повідомлення
                                    onAuthSuccess()
                                }
                            }
                        },
                        onSwitchToLogin = {
                            isRegisterMode = false
                            result = null
                        }
                    )
                } else {
                    LoginForm(
                        isLoading = isLoading,
                        onLogin = { login, password ->
                            scope.launch {
                                isLoading = true
                                result = null
                                delay(1200) // імітація перевірки на сервері
                                val validation = AuthValidator.validateLogin(login, password)
                                result = validation
                                isLoading = false
                                if (validation.isSuccess) {
                                    delay(600) // даємо користувачу побачити повідомлення
                                    onAuthSuccess()
                                }
                            }
                        },
                        onSwitchToRegister = {
                            isRegisterMode = true
                            result = null
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Перевірка облікових даних...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    result?.let { r ->
                        AuthMessage(message = r.message, isSuccess = r.isSuccess)
                    }
                }
            }
        }
    }
}
