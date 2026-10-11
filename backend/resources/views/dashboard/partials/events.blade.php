@php($d = \App\Support\DashboardDisplay::class)
@if($events->isEmpty())<div class="empty"><span aria-hidden="true">≋</span><h3>No hay eventos disponibles para este período.</h3><p>Solo se muestran eventos recibidos. No se generan registros para completar esta vista.</p></div>
@else<div class="table-wrap"><table><caption class="sr-only">Eventos recibidos</caption><thead><tr><th>Evento / UUID</th><th>Tipo</th><th>Origen</th><th>Fecha · Lima</th><th>Ubicación GPS</th>@if(!isset($hideTrip))<th>Recorrido</th>@endif</tr></thead><tbody>
@foreach($events as $event)<tr><td class="uuid">{{ $event->client_event_id }}</td><td>{{ $d::type($event->type) }}<small class="code">{{ $event->type }}</small></td><td><span class="badge simulated">{{ $d::origin($event->origin) }}</span></td><td class="date">{{ $d::date($event->occurred_at) }}</td><td>
@if($event->latitude !== null && $event->longitude !== null && $event->location_origin !== null)
<div>Latitud: {{ $event->latitude }}</div><div>Longitud: {{ $event->longitude }}</div>
<small>Origen de ubicación: {{ $event->location_origin }}</small>
@if($event->gps_received_at)<small>Recepción GPS · Lima: {{ $d::date($event->gps_received_at) }}</small>@endif
@if($event->gps_utc_seconds !== null)<small>Hora GPS UTC (sin fecha): {{ sprintf('%02d:%02d:%02d', intdiv($event->gps_utc_seconds, 3600), intdiv($event->gps_utc_seconds % 3600, 60), $event->gps_utc_seconds % 60) }}</small>@endif
@if($event->gps_age_ms !== null)<small>Antigüedad al crear el evento: {{ $event->gps_age_ms }} ms</small>@endif
@else
<span class="muted">Ubicación no disponible</span>
@endif
</td>@if(!isset($hideTrip))<td><a class="uuid" href="{{ route('dashboard.trip', $event->trip->client_trip_id) }}">{{ $event->trip->client_trip_id }}</a></td>@endif</tr>@endforeach
</tbody></table></div>@endif
