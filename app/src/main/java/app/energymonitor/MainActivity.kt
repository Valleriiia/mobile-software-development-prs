package app.energymonitor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.energymonitor.data.AppContainer
import app.energymonitor.data.auth.Session
import app.energymonitor.data.auth.SessionEvent
import app.energymonitor.domain.Permissions
import app.energymonitor.ui.auth.AuthScreen
import app.energymonitor.ui.calculator.CalculatorScreen
import app.energymonitor.ui.common.AccessDeniedScreen
import app.energymonitor.ui.dashboard.DashboardScreen
import app.energymonitor.ui.menu.MainMenuScreen
import app.energymonitor.ui.meters.MetersScreen
import app.energymonitor.ui.monitor.MonitorScreen
import app.energymonitor.ui.nav.Routes
import app.energymonitor.ui.profile.ProfileScreen
import app.energymonitor.ui.reading.AddReadingScreen
import app.energymonitor.ui.reading.ReadingsListScreen
import app.energymonitor.ui.theme.EnergyMonitorTheme
import app.energymonitor.viewmodel.AuthViewModel
import app.energymonitor.viewmodel.AuthViewModelFactory
import app.energymonitor.viewmodel.MetersViewModel
import app.energymonitor.viewmodel.MetersViewModelFactory
import app.energymonitor.viewmodel.ReadingsViewModel
import app.energymonitor.viewmodel.ReadingsViewModelFactory
import kotlinx.coroutines.delay

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
 * Кореневий composable: вирішує, що показати, екран входу чи основний застосунок.
 * Рішення залежить від сесії (SessionManager): поки її немає, навігаційний граф
 * не створюється. Вихід, відхилення токена сервером і закінчення його терміну
 * дії лише очищують сесію, а повернення на екран входу відбувається саме собою.
 */
@Composable
fun EnergyMonitorApp() {
    // Контейнер залежностей живе стільки, скільки процес: поворот екрана не створює
    // другу базу і не "скидає" мок-сервер (див. AppContainer).
    val container = AppContainer.get(LocalContext.current)
    val session by container.sessionManager.session.collectAsState()
    val authViewModel: AuthViewModel =
        viewModel(factory = AuthViewModelFactory(container.authRepository))
    var notice by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        container.sessionManager.events.collect { event ->
            if (event is SessionEvent.Expired) notice = event.message
        }
    }

    // Токен може закінчитися, поки користувач просто дивиться на екран і нічого
    // не надсилає, тож чекаємо до моменту закінчення й завершуємо сесію самі.
    LaunchedEffect(session) {
        val current = session ?: return@LaunchedEffect
        notice = null
        val remaining = current.expiresAtMillis - System.currentTimeMillis()
        if (remaining > 0) delay(remaining)
        container.sessionManager.expire("Термін дії сесії минув. Увійдіть знову")
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        val current = session
        if (current == null) {
            AuthScreen(viewModel = authViewModel, notice = notice)
        } else {
            EnergyMonitorNavGraph(container = container, session = current)
        }
    }
}

/**
 * Навігаційний граф: зіставляє кожен маршрут із Routes з екраном.
 * Налаштування notifyOnOverload підняте (hoisted) на цей рівень, щоб
 * зберігатися при переходах між екранами. Кнопка "назад" на екранах
 * викликає navController.popBackStack(): знімає поточний екран зі стеку
 * і повертає на попередній. Права з ролі сесії визначають, які дії доступні.
 */
@Composable
private fun EnergyMonitorNavGraph(container: AppContainer, session: Session) {
    var notifyOnOverload by remember { mutableStateOf(true) }
    val readingsViewModel: ReadingsViewModel =
        viewModel(factory = ReadingsViewModelFactory(container.readingsRepository))
    val metersViewModel: MetersViewModel =
        viewModel(factory = MetersViewModelFactory(container.metersRepository, container.readingsRepository))
    val navController = rememberNavController()
    val canEdit = Permissions.canEditReadings(session.role)

    NavHost(navController = navController, startDestination = Routes.MENU) {
        // Головне меню: стартовий екран, "назад" на ньому не потрібна
        composable(Routes.MENU) {
            MainMenuScreen(
                onNavigate = { route -> navController.navigate(route) },
                canAddReadings = canEdit
            )
        }
        // Екрани-призначення: у кожного є onBack для повернення в меню
        composable(Routes.MONITOR) { MonitorScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.CALCULATOR) { CalculatorScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.DASHBOARD) { DashboardScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.ADD_READING) {
            if (canEdit) {
                AddReadingScreen(viewModel = readingsViewModel, onBack = { navController.popBackStack() })
            } else {
                AccessDeniedScreen(onBack = { navController.popBackStack() })
            }
        }
        composable(Routes.READINGS_LIST) {
            ReadingsListScreen(
                viewModel = readingsViewModel,
                onBack = { navController.popBackStack() },
                canEdit = canEdit
            )
        }
        composable(Routes.METERS) { MetersScreen(viewModel = metersViewModel, onBack = { navController.popBackStack() }) }
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
                session = session,
                backend = container.backend,
                mockAuth = container.mockAuth,
                onLogout = { container.authRepository.logout() },
                onBack = { navController.popBackStack() }
            )
        }
    }
}
