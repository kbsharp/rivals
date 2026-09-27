package com.kevinbevan.rivals.ui.history

import android.text.format.DateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private val timeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

/**
 * Weekday, day, month and year in the phone's locale and time zone: "Tue 22 Sept 2026" in the UK,
 * "Tue, Sep 22, 2026" in the US. Android picks the order and punctuation for the locale.
 */
fun formatDay(instant: Instant?): String {
    val locale = Locale.getDefault()
    val pattern = DateFormat.getBestDateTimePattern(locale, "EEEdMMMyyyy")
    return instant?.atZone(ZoneId.systemDefault())
        ?.format(DateTimeFormatter.ofPattern(pattern, locale)) ?: "Date unknown"
}

/** "19:30 – 23:10", or just the start while it's open-ended. */
fun formatTimes(start: Instant?, end: Instant?): String {
    val zone = ZoneId.systemDefault()
    val from = start?.atZone(zone)?.format(timeFormat) ?: return ""
    val to = end?.atZone(zone)?.format(timeFormat) ?: return from
    return "$from – $to"
}
