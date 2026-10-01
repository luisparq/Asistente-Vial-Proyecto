package com.tecsup.asistente.conduccion.core.ui

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Presentation only. Resolve the device zone on each call, never change the stored Instant. */
fun formatTimestamp(
    time: Instant,
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
): String = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
    .withLocale(locale).withZone(zone).format(time) + " (${zone.id})"
