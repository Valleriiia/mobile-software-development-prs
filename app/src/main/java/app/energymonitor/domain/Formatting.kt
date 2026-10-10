package app.energymonitor.domain

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun Double.fmt(digits: Int = 2): String =
    String.format(Locale.US, "%.${digits}f", this)

fun Float.fmt(digits: Int = 2): String =
    String.format(Locale.US, "%.${digits}f", this)

fun Int.asHourLabel(): String = String.format(Locale.US, "%02d:00", this)

private val recordDateFormat = SimpleDateFormat("dd.MM HH:mm", Locale.US)

/** Мітка часу (мс) -> підпис виду "20.09 14:32". */
fun Long.asDateTimeLabel(): String = recordDateFormat.format(Date(this))
