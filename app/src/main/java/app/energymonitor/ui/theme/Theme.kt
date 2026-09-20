package app.energymonitor.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = EnergyBlue,
    onPrimary = Color.White,
    secondary = EnergyTeal,
    onSecondary = Color.White,
    tertiary = EnergyAmber,
    background = EnergySurface,
    onBackground = EnergyOnSurface,
    surface = Color.White,
    onSurface = EnergyOnSurface
)

private val DarkColors = darkColorScheme(
    primary = EnergyBlueDark,
    onPrimary = Color(0xFF00305F),
    secondary = EnergyTealDark,
    onSecondary = Color(0xFF00382F),
    tertiary = EnergyAmberDark,
    background = EnergySurfaceDark,
    onBackground = Color(0xFFE3E2E6),
    surface = Color(0xFF1B1E22),
    onSurface = Color(0xFFE3E2E6)
)

// dynamicColor вимкнено за замовчуванням, щоб на всіх пристроях і
// скріншотах фірмові кольори застосунку виглядали однаково (інакше на
// Android 12+ Material You підмінить їх кольорами шпалер користувача).
@Composable
fun EnergyMonitorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
