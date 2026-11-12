package com.natan.obd.ui

import java.util.Locale

/** 75 -> "1:15", 3725 -> "1:02:05". */
fun formatDuration(totalSec: Int): String {
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) String.format(Locale.getDefault(), "%d:%02d:%02d", h, m, s)
    else String.format(Locale.getDefault(), "%d:%02d", m, s)
}

fun formatDecimal(value: Double, digits: Int = 1): String =
    String.format(Locale.getDefault(), "%.${digits}f", value)
