package app.energymonitor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.energymonitor.ui.auth.AuthScreen
import app.energymonitor.ui.calculator.CalculatorScreen
import app.energymonitor.ui.dashboard.DashboardScreen
import app.energymonitor.ui.menu.MainMenuScreen
import app.energymonitor.ui.monitor.MonitorScreen
import app.energymonitor.ui.nav.Routes
import app.energymonitor.ui.profile.ProfileScreen
import app.energymonitor.ui.theme.EnergyMonitorTheme

/**
 * Єдина Activity застосунку. Лише підключає тему Material і кореневий
 * composable EnergyMonitorApp, а вся навігація та екрани живуть у Compose.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EnergyMonitorTheme {
                EnergyMonitorApp()
            }
        }
    }
}

/**
 * Кореневий composable: вирішує, що показати, екран авторизації чи основний
 * застосунок. Прапорець isAuthenticated живе на самому верху, тому
 * навігаційний граф не створюється, доки користувач не увійшов.
 */
@Composable
fun EnergyMonitorApp() {
    var isAuthenticated by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (!isAuthenticated) {
            AuthScreen(onAuthSuccess = { isAuthenticated = true })
        } else {
            EnergyMonitorNavGraph()
        }
    }
}

/**
 * Навігаційний граф: зіставляє кожен маршрут із Routes з екраном.
 * Налаштування notifyOnOverload підняте (hoisted) на цей рівень, щоб
 * зберігатися при переходах між екранами. Кнопка "назад" на екранах
 * викликає navController.popBackStack(): знімає поточний екран зі стеку
 * і повертає на попередній.
 */
@Composable
private fun EnergyMonitorNavGraph() {
    var notifyOnOverload by remember { mutableStateOf(true) }
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.MENU) {
        // Головне меню: стартовий екран, "назад" на ньому не потрібна
        composable(Routes.MENU) {
            MainMenuScreen(onNavigate = { route -> navController.navigate(route) })
        }
        // Екрани-призначення: у кожного є onBack для повернення в меню
        composable(Routes.MONITOR) { MonitorScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.CALCULATOR) { CalculatorScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.DASHBOARD) { DashboardScreen(onBack = { navController.popBackStack() }) }
        // Маршрут з аргументом: {fromScreen} підставляється під час navigate()
        // і зчитується з backStackEntry.arguments
        composable(
            route = Routes.PROFILE_PATTERN,
            arguments = listOf(navArgument("fromScreen") { type = NavType.StringType })
        ) { backStackEntry ->
            ProfileScreen(
                fromScreen = backStackEntry.arguments?.getString("fromScreen") ?: Routes.MENU,
                notifyOnOverload = notifyOnOverload,
                onToggleNotify = { notifyOnOverload = it },
                onBack = { navController.popBackStack() }
            )
        }
    }
}