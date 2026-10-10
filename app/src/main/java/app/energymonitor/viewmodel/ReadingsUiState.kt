package app.energymonitor.viewmodel

/** Стан операції збереження показника — окремо від самих даних (readings). */
sealed interface UiState {
    data object Idle : UiState
    data object Loading : UiState
    data class Success(val message: String) : UiState
    data class Error(val message: String) : UiState
}

/** Порядок сортування списку показників. */
enum class SortOrder(val title: String) {
    NEWEST("Новіші спочатку"),
    OLDEST("Старіші спочатку"),
    POWER_DESC("За потужністю")
}