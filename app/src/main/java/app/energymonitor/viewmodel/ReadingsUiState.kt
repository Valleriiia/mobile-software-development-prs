package app.energymonitor.viewmodel

/** Стан операції збереження/редагування/видалення — окремо від самих даних (readings). */
sealed interface UiState {
    data object Idle : UiState
    data object Loading : UiState
    data class Success(val message: String) : UiState
    data class Error(val message: String) : UiState
}

enum class SortOrder(val title: String) {
    NEWEST("Новіші спочатку"),
    OLDEST("Старіші спочатку"),
    POWER_DESC("За потужністю")
}

/**
 * З якого джерела екран показує дані:
 *  - LOCAL — лише кеш у Room (працює без мережі);
 *  - REMOTE — свіжий знімок із сервера, без запису в кеш;
 *  - AGGREGATED — кеш, який Repository автоматично оновлює із сервера.
 */
enum class DataSourceMode(val title: String) {
    LOCAL("Локально"),
    REMOTE("Віддалено"),
    AGGREGATED("Агреговано")
}
