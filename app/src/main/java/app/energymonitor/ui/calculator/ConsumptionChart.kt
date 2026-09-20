package app.energymonitor.ui.calculator

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import app.energymonitor.domain.ConsumptionPoint

// MPAndroidChart побудована на класичних Android View, тому в Compose
// підключається через міст AndroidView: factory{} створює View один раз,
// update{} оновлює дані при кожній зміні стану.

/** Графік добового профілю споживання електроенергії. */
@Composable
fun ConsumptionChart(
    profile: List<ConsumptionPoint>,
    lineLabel: String = "Потужність, кВт",
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .height(260.dp),
        factory = { context ->
            LineChart(context).apply {
                description.isEnabled = false
                setTouchEnabled(true)
                setScaleEnabled(true)
                setPinchZoom(true)
                setNoDataText("Немає даних для відображення")

                axisRight.isEnabled = false
                axisLeft.axisMinimum = 0f
                axisLeft.setDrawGridLines(true)

                xAxis.position = XAxis.XAxisPosition.BOTTOM
                xAxis.granularity = 1f
                xAxis.setDrawGridLines(false)
                xAxis.valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String =
                        "${value.toInt()}"
                }
            }
        },
        update = { chart ->
            val entries = profile.map { point ->
                Entry(point.hour.toFloat(), point.powerKw.toFloat())
            }

            val dataSet = LineDataSet(entries, lineLabel).apply {
                setColor(AndroidColor.rgb(13, 71, 161))
                setLineWidth(2.2f)
                setCircleColor(AndroidColor.rgb(13, 71, 161))
                setCircleRadius(3f)
                setDrawCircleHole(false)
                setDrawValues(false)
                setDrawFilled(true)
                setFillColor(AndroidColor.rgb(100, 149, 237))
                setFillAlpha(70)
                setMode(LineDataSet.Mode.CUBIC_BEZIER)
            }

            chart.data = LineData(dataSet)
            chart.invalidate()
        }
    )
}
