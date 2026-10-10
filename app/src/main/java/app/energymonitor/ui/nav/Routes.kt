package app.energymonitor.ui.nav

object Routes {
    // Головне меню: стартовий екран графа
    const val MENU = "menu"

    // Поточні показники лічильника з анімацією
    const val MONITOR = "monitor"

    // Розрахунок потужності та вартості, графік добового профілю
    const val CALCULATOR = "calculator"

    // Панель з режимами роботи та переліком споживачів
    const val DASHBOARD = "dashboard"

    // Шаблон маршруту з аргументом, {fromScreen} замінюється значенням у profile()
    const val PROFILE_PATTERN = "profile/{fromScreen}"

    // Будує готовий маршрут для navigate(), підставляючи назву екрана
    fun profile(fromScreen: String) = "profile/$fromScreen"
}