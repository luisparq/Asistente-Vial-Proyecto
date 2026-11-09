package com.tecsup.asistente.conduccion.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tecsup.asistente.conduccion.R
import com.tecsup.asistente.conduccion.domain.model.DataOrigin
import com.tecsup.asistente.conduccion.domain.model.DrivingEvent
import com.tecsup.asistente.conduccion.domain.model.DrivingEventType
import com.tecsup.asistente.conduccion.domain.model.Trip
import com.tecsup.asistente.conduccion.domain.model.TripStatus

fun DrivingEventType.labelResource(): Int = when (this) {
    DrivingEventType.HARSH_BRAKING -> R.string.event_braking
    DrivingEventType.HARSH_ACCELERATION -> R.string.event_acceleration
    DrivingEventType.SHARP_TURN -> R.string.event_turn
}

/** Tarjeta visual común de recorridos y eventos. */
@Composable
fun InfoCard(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
fun TripInfo(trip: Trip) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.trip_number, trip.id), style = MaterialTheme.typography.titleMedium)
        val isActive = trip.status == TripStatus.ACTIVE
        StatusBadge(stringResource(if (isActive) R.string.trip_active else R.string.trip_completed), if (isActive) StatusTone.PROGRESS else StatusTone.OK)
    }
    Text(stringResource(R.string.started_at, formatTimestamp(trip.startedAt)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(trip.endedAt?.let { stringResource(R.string.ended_at, formatTimestamp(it)) }
        ?: stringResource(R.string.not_finished), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun EventInfo(event: DrivingEvent) {
    var gpsVisible by remember(event.id) { mutableStateOf(false) }
    Text(stringResource(event.type.labelResource()), style = MaterialTheme.typography.titleSmall)
    EvidenceLabel(stringResource(
        if (event.origin == DataOrigin.SIMULATED) R.string.origin_simulated else R.string.origin_sensor,
    ))
    Text("Fecha y hora del evento: " + formatTimestamp(event.occurredAt), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (event.locationOrigin == com.tecsup.asistente.conduccion.domain.model.LocationOrigin.GPS_NEO6M && event.latitude != null && event.longitude != null) {
        Text("Ubicación GPS disponible", style = MaterialTheme.typography.bodyMedium)
        Text("El evento sigue siendo SIMULATED: dato de prueba, no detección física.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = { gpsVisible = !gpsVisible }) { Text(if (gpsVisible) "Ocultar datos GPS" else "Ver datos GPS") }
        if (gpsVisible) Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val style = MaterialTheme.typography.bodySmall
            Text("GPS NEO-6M: ${event.latitude}, ${event.longitude}", style = style)
            event.gpsReceivedAt?.let { Text("Recepción GPS: " + formatTimestamp(it), style = style) }
            event.gpsUtcSeconds?.let { Text("Hora GPS UTC (sin fecha): " + java.time.LocalTime.ofSecondOfDay(it.toLong()), style = style) }
            event.gpsAgeMillis?.let { Text("Antigüedad GPS al crear el evento: $it ms", style = style) }
        }
    } else Text("Sin ubicación GPS válida asociada", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun ReadError() {
    Text(stringResource(R.string.read_error), color = MaterialTheme.colorScheme.error)
}
