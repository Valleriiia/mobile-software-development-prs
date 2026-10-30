package app.energymonitor.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.energymonitor.data.auth.AuthRepository
import app.energymonitor.domain.AuthValidator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

/** Стан форми входу/реєстрації. Успіх окремого стану не має: сесія стартує, і екран зникає. */
sealed interface AuthUiState {
    data object Idle : AuthUiState
    data object Loading : AuthUiState
    data class Failed(val message: String) : AuthUiState
}

/**
 * Спершу локальна перевірка полів (AuthValidator, без запиту), і лише потім
 * звернення до сервера. Відповідь сервера перетворюється на зрозумілий текст.
 */
class AuthViewModel(private val repository: AuthRepository) : ViewModel() {

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun login(login: String, password: String) {
        val validation = AuthValidator.validateLogin(login, password)
        if (!validation.isSuccess) {
            _uiState.value = AuthUiState.Failed(validation.message)
            return
        }
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            repository.login(login, password).fold(
                onSuccess = { _uiState.value = AuthUiState.Idle },
                onFailure = { _uiState.value = AuthUiState.Failed(describe(it)) }
            )
        }
    }

    fun register(name: String, email: String, password: String, confirm: String) {
        val validation = AuthValidator.validateRegistration(name, email, password, confirm)
        if (!validation.isSuccess) {
            _uiState.value = AuthUiState.Failed(validation.message)
            return
        }
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            repository.register(name, email, password).fold(
                onSuccess = { _uiState.value = AuthUiState.Idle },
                onFailure = { _uiState.value = AuthUiState.Failed(describe(it)) }
            )
        }
    }

    fun clearMessage() {
        _uiState.value = AuthUiState.Idle
    }

    private fun describe(error: Throwable): String = when {
        error is HttpException && error.code() == 401 -> "Невірний логін або пароль"
        error is HttpException && error.code() == 409 -> "Користувач із такою поштою вже існує"
        else -> "Не вдалося виконати запит: ${error.message ?: error::class.java.simpleName}"
    }
}

class AuthViewModelFactory(private val repository: AuthRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = AuthViewModel(repository) as T
}
