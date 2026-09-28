package com.tecsup.asistente.conduccion.core.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.tecsup.asistente.conduccion.R
import com.tecsup.asistente.conduccion.domain.model.DataOrigin
import com.tecsup.asistente.conduccion.domain.model.DrivingEvent
import com.tecsup.asistente.conduccion.domain.model.DrivingEventType
import com.tecsup.asistente.conduccion.domain.model.Trip
import com.tecsup.asistente.conduccion.domain.model.TripStatus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

fun formatTimestamp(time: Instant): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
        .withZone(ZoneId.systemDefault()).format(time)

fun DrivingEventType.labelResource(): Int = when (this) {
    DrivingEventType.HARSH_BRAKING -> R.string.event_braking
    DrivingEventType.HARSH_ACCELERATION -> R.string.event_acceleration
    DrivingEventType.SHARP_TURN -> R.string.event_turn
}

@Composable
fun TripInfo(trip: Trip) {
    Text(stringResource(R.string.trip_number, trip.id), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(if (trip.status == TripStatus.ACTIVE) R.string.trip_active else R.string.trip_completed))
    Text(stringResource(R.string.started_at, formatTimestamp(trip.startedAt)))
    Text(trip.endedAt?.let { stringResource(R.string.ended_at, formatTimestamp(it)) }
        ?: stringResource(R.string.not_finished))
}

@Composable
fun EventInfo(event: DrivingEvent) {
    Text(stringResource(event.type.labelResource()), style = MaterialTheme.typography.titleSmall)
    Text(stringResource(
        if (event.origin == DataOrigin.SIMULATED) R.string.origin_simulated else R.string.origin_sensor,
    ))
    Text(formatTimestamp(event.occurredAt))
}

@Composable
fun ReadError() {
    Text(stringResource(R.string.read_error), color = MaterialTheme.colorScheme.error)
}
