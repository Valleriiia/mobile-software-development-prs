package app.energymonitor.domain

import java.util.Locale

fun Double.fmt(digits: Int = 2): String =
    String.format(Locale.US, "%.${digits}f", this)

fun Float.fmt(digits: Int = 2): String =
    String.format(Locale.US, "%.${digits}f", this)
