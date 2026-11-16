package app.energymonitor.domain

/** Географічна точка (градуси). Не залежить від Google Maps: його LatLng лишається в UI-шарі. */
data class GeoPoint(val latitude: Double, val longitude: Double) {
    val isValid: Boolean get() = latitude in -90.0..90.0 && longitude in -180.0..180.0
}

/** Прямокутник, що охоплює набір точок; потрібен, щоб камера мапи показала всі маркери. */
data class GeoBounds(
    val south: Double,
    val west: Double,
    val north: Double,
    val east: Double
) {
    val center: GeoPoint get() = GeoPoint((south + north) / 2.0, (west + east) / 2.0)

    /** Усі точки збігаються (або точка одна): масштабувати "під рамку" немає сенсу. */
    val isSinglePoint: Boolean get() = south == north && west == east

    companion object {
        fun of(points: List<GeoPoint>): GeoBounds? {
            if (points.isEmpty()) return null
            return GeoBounds(
                south = points.minOf { it.latitude },
                west = points.minOf { it.longitude },
                north = points.maxOf { it.latitude },
                east = points.maxOf { it.longitude }
            )
        }
    }
}
