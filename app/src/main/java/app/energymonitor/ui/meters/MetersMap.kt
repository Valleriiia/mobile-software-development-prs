package app.energymonitor.ui.meters

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.energymonitor.domain.GeoBounds
import app.energymonitor.domain.GeoPoint
import app.energymonitor.domain.LoadLevel
import app.energymonitor.domain.MeterCardData
import app.energymonitor.domain.fmt
import app.energymonitor.ui.theme.LoadNormal
import app.energymonitor.ui.theme.LoadOverload
import app.energymonitor.ui.theme.LoadWarning
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.GoogleMapComposable
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Card
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState

private const val FOCUS_ZOOM = 17.5f
private const val OVERVIEW_PADDING_PX = 120

// Початкова позиція камери до завантаження мапи: центр кампусу з умовних координат.
private val DEFAULT_CENTER = LatLng(50.4498, 30.4628)

private fun GeoPoint.toLatLng() = LatLng(latitude, longitude)

/**
 * Мапа з маркерами точок обліку. Компонент нічого не вирішує сам:
 *  - які маркери показувати, береться зі списку cards (його вже відфільтровано ViewModel);
 *  - який маркер виділено, береться зі selectedId;
 *  - клік по маркеру чи порожньому місцю повертається нагору через onSelect.
 * Завдяки цьому список і мапа не знають один про одного: обидва читають один стан.
 */
@Composable
fun MetersMap(
    cards: List<MeterCardData>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(DEFAULT_CENTER, 16f)
    }
    var mapLoaded by remember { mutableStateOf(false) }

    val mapped = remember(cards) { cards.filter { it.meter.position != null } }
    val visibleIds = remember(mapped) { mapped.map { it.meter.id } }

    // Коли набір видимих точок змінився (завантаження, фільтр), камера охоплює їх усі.
    LaunchedEffect(mapLoaded, visibleIds) {
        if (!mapLoaded) return@LaunchedEffect
        val bounds = GeoBounds.of(mapped.mapNotNull { it.meter.position }) ?: return@LaunchedEffect
        val update = if (bounds.isSinglePoint) {
            CameraUpdateFactory.newLatLngZoom(bounds.center.toLatLng(), FOCUS_ZOOM)
        } else {
            CameraUpdateFactory.newLatLngBounds(
                LatLngBounds(LatLng(bounds.south, bounds.west), LatLng(bounds.north, bounds.east)),
                OVERVIEW_PADDING_PX
            )
        }
        cameraPositionState.animate(update, 400)
    }

    // Вибір зі списку (або з маркера) наближає камеру до вибраної точки.
    LaunchedEffect(selectedId) {
        val point = mapped.firstOrNull { it.meter.id == selectedId }?.meter?.position ?: return@LaunchedEffect
        cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(point.toLatLng(), FOCUS_ZOOM), 600)
    }

    val selectedCard = mapped.firstOrNull { it.meter.id == selectedId }

    Column(modifier = modifier) {
        Box(modifier = Modifier.weight(1f)) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                uiSettings = MapUiSettings(zoomControlsEnabled = true, mapToolbarEnabled = false),
                onMapLoaded = { mapLoaded = true },
                onMapClick = { onSelect(null) }
            ) {
                mapped.forEach { card ->
                    MeterMarker(
                        card = card,
                        selected = card.meter.id == selectedId,
                        onClick = { onSelect(card.meter.id) }
                    )
                }
            }
            // Деталі вибраної точки: звичайна Compose-картка поверх мапи
            if (selectedCard != null) {
                MapInfoCard(
                    card = selectedCard,
                    onClose = { onSelect(null) },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 8.dp, end = 56.dp, bottom = 8.dp)
                )
            }
        }
        MapLegend(modifier = Modifier.padding(top = 4.dp))
    }
}

/**
 * Маркер точки обліку. Колір відповідає рівню навантаження, вибраний маркер синій
 * і лежить поверх інших. Інформаційне вікно (InfoWindow) відкривається саме для
 * вибраної точки, тож клік по картці в списку теж показує його на мапі.
 */
@Composable
@GoogleMapComposable
private fun MeterMarker(card: MeterCardData, selected: Boolean, onClick: () -> Unit) {
    val position = card.meter.position ?: return
    val markerState = rememberMarkerState(key = card.meter.id, position = position.toLatLng())

    val hue = when {
        selected -> BitmapDescriptorFactory.HUE_AZURE
        card.level == LoadLevel.NORMAL -> BitmapDescriptorFactory.HUE_GREEN
        card.level == LoadLevel.WARNING -> BitmapDescriptorFactory.HUE_ORANGE
        card.level == LoadLevel.OVERLOAD -> BitmapDescriptorFactory.HUE_RED
        else -> BitmapDescriptorFactory.HUE_YELLOW
    }

    Marker(
        state = markerState,
        icon = BitmapDescriptorFactory.defaultMarker(hue),
        zIndex = if (selected) 1f else 0f,
        onClick = {
            onClick()
            true // стандартне вікно не потрібне: деталі показує MapInfoCard
        }
    )
}

@Composable
private fun MapLegend(modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        LegendItem("норма", LoadNormal)
        LegendItem("підвищене", LoadWarning)
        LegendItem("перевантаження", LoadOverload)
        LegendItem("вибрано", Color(0xFF1E88E5))
    }
}

@Composable
private fun LegendItem(text: String, color: Color) {
    Row {
        Text("●", color = color, style = MaterialTheme.typography.labelSmall)
        Text(
            text = " $text",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Компактна картка вибраної точки поверх мапи */
@Composable
private fun MapInfoCard(card: MeterCardData, onClose: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 0.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${card.meter.name} · ${card.meter.type.title}",
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1
                )
                Text(
                    text = card.powerKw?.let { "Навантаження: ${it.fmt(2)} кВт (${card.level?.title})" }
                        ?: "Показників ще немає",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            TextButton(onClick = onClose) { Text("✕") }
        }
    }
}
