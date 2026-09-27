package com.kevinbevan.rivals.ui.history

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private val dayFormat = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.US)
private val timeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

/** "Tue, Sep 22, 2026", in the phone's time zone. */
fun formatDay(instant: Instant?): String =
    instant?.atZone(ZoneId.systemDefault())?.format(dayFormat) ?: "Date unknown"

/** "19:30 – 23:10", or just the start while it's open-ended. */
fun formatTimes(start: Instant?, end: Instant?): String {
    val zone = ZoneId.systemDefault()
    val from = start?.atZone(zone)?.format(timeFormat) ?: return ""
    val to = end?.atZone(zone)?.format(timeFormat) ?: return from
    return "$from – $to"
}
