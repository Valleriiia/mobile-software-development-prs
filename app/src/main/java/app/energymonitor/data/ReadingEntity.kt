package app.energymonitor.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import app.energymonitor.domain.ReadingRecord

@Entity(tableName = "readings")
data class ReadingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val timestampMillis: Long,
    val voltage: Double,
    val current: Double,
    val powerFactor: Double
)

fun ReadingEntity.toRecord() = ReadingRecord(id, label, timestampMillis, voltage, current, powerFactor)

fun ReadingRecord.toEntity() = ReadingEntity(id, label, timestampMillis, voltage, current, powerFactor)
