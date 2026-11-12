package app.energymonitor.ui.live

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.unit.dp
import app.energymonitor.ui.theme.LoadOverload

/**
 * Невеликий графік потужності "наживо" без сторонніх бібліотек: суцільна лінія
 * для фактичних значень, пунктирна для прогнозу тренду і червона пунктирна
 * межа договірної потужності. Вісь Y масштабується під дані.
 */
@Composable
fun LivePowerChart(
    power: List<Double>,
    forecast: List<Double>,
    limitKw: Double,
    powerColor: Color,
    forecastColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(160.dp)
    ) {
        val maxValue = maxOf(limitKw * 1.15, (power.maxOrNull() ?: 0.0) * 1.1, (forecast.maxOrNull() ?: 0.0) * 1.1)
        fun y(value: Double) = size.height - (value / maxValue * size.height).toFloat()

        drawLine(
            color = LoadOverload,
            start = Offset(0f, y(limitKw)),
            end = Offset(size.width, y(limitKw)),
            strokeWidth = 2f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f))
        )

        fun drawSeries(values: List<Double>, color: Color, dashed: Boolean) {
            if (values.size < 2) return
            val stepX = size.width / (HISTORY_POINTS - 1)
            // Нові значення з'являються праворуч, тож ряд вирівнюється за правим краєм.
            val startIndex = HISTORY_POINTS - values.size
            for (index in 1 until values.size) {
                drawLine(
                    color = color,
                    start = Offset((startIndex + index - 1) * stepX, y(values[index - 1])),
                    end = Offset((startIndex + index) * stepX, y(values[index])),
                    strokeWidth = if (dashed) 3f else 5f,
                    pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(10f, 8f)) else null
                )
            }
        }

        drawSeries(forecast, forecastColor, dashed = true)
        drawSeries(power, powerColor, dashed = false)
    }
}

private const val HISTORY_POINTS = 40
