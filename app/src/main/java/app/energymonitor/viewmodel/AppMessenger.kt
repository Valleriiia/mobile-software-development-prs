package app.energymonitor.viewmodel

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Коротке повідомлення для Snackbar; onAction виконується, якщо користувач натиснув кнопку дії. */
data class UiMessage(
    val text: String,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null
)

/**
 * Канал "ViewModel -> екран" для разових повідомлень. Це подія, а не стан:
 * Snackbar треба показати один раз, тому SharedFlow без повторного програвання,
 * а не StateFlow (той показав би старе повідомлення знову після повороту екрана).
 */
class AppMessenger {

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 8)
    val messages: SharedFlow<UiMessage> = _messages.asSharedFlow()

    fun info(text: String) {
        _messages.tryEmit(UiMessage(text))
    }

    fun error(text: String, retry: (() -> Unit)? = null) {
        _messages.tryEmit(UiMessage(text, actionLabel = retry?.let { "Повторити" }, onAction = retry))
    }
}
