package app.energymonitor.data.remote.dto

import app.energymonitor.domain.Meter
import app.energymonitor.domain.ReadingRecord
import app.energymonitor.domain.Tariff
import com.google.gson.annotations.SerializedName

/*
 * Усі поля DTO — nullable. Gson створює об'єкти в обхід конструктора Kotlin і не
 * перевіряє null-безпеку, тому відсутнє у відповіді поле інакше стало б прихованим
 * null у "не-null" властивості. Перетворення в доменну модель перевіряє дані й
 * відхиляє некоректні записи замість аварії застосунку.
 */

data class ReadingDto(
    @SerializedName("id") val id: Long?,
    @SerializedName("label") val label: String?,
    @SerializedName("timestamp") val timestampMillis: Long?,
    @SerializedName("voltage") val voltage: Double?,
    @SerializedName("current") val current: Double?,
    @SerializedName("power_factor") val powerFactor: Double?
)

data class LocationDto(
    @SerializedName("building") val building: String?,
    @SerializedName("floor") val floor: Int?
)

data class MeterDto(
    @SerializedName("id") val id: String?,
    @SerializedName("name") val name: String?,
    @SerializedName("location") val location: LocationDto?,
    @SerializedName("contract_power_kw") val contractPowerKw: Double?
)

/** Обгортка зі вкладеним масивом: сервер повертає не голий список, а об'єкт. */
data class MetersResponse(
    @SerializedName("meters") val meters: List<MeterDto>?,
    @SerializedName("updated_at") val updatedAt: Long?
)

data class TariffDto(
    @SerializedName("zone") val zone: String?,
    @SerializedName("price_per_kwh") val pricePerKwh: Double?,
    @SerializedName("hours_per_day") val hoursPerDay: Int?
)

data class ApiErrorDto(
    @SerializedName("error") val error: String?,
    @SerializedName("message") val message: String?
)

fun ReadingDto.toDomainOrNull(): ReadingRecord? {
    val id = id ?: return null
    val label = label?.takeIf { it.isNotBlank() } ?: return null
    val timestamp = timestampMillis ?: return null
    val voltage = voltage?.takeIf { it > 0.0 } ?: return null
    val current = current?.takeIf { it >= 0.0 } ?: return null
    val powerFactor = powerFactor?.takeIf { it in 0.0..1.0 } ?: return null
    return ReadingRecord(id, label.trim(), timestamp, voltage, current, powerFactor)
}

fun ReadingRecord.toDto() = ReadingDto(id, label, timestampMillis, voltage, current, powerFactor)

fun MeterDto.toDomainOrNull(): Meter? {
    val id = id?.takeIf { it.isNotBlank() } ?: return null
    val name = name?.takeIf { it.isNotBlank() } ?: return null
    val power = contractPowerKw?.takeIf { it > 0.0 } ?: return null
    return Meter(
        id = id,
        name = name.trim(),
        building = location?.building ?: "—",
        floor = location?.floor ?: 0,
        contractPowerKw = power
    )
}

fun TariffDto.toDomainOrNull(): Tariff? {
    val zone = zone?.takeIf { it.isNotBlank() } ?: return null
    val price = pricePerKwh?.takeIf { it >= 0.0 } ?: return null
    val hours = hoursPerDay?.takeIf { it in 1..24 } ?: return null
    return Tariff(zone, price, hours)
}
