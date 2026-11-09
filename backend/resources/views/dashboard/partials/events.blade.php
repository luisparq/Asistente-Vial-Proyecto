@php($d = \App\Support\DashboardDisplay::class)
@if($events->isEmpty())<div class="empty"><x-nav-icon name="events"/><h3>No hay eventos disponibles para este período.</h3><p>Solo se muestran eventos recibidos. No se generan registros para completar esta vista.</p></div>
@else<div class="table-wrap"><table><caption class="sr-only">Eventos recibidos</caption><thead><tr><th>Evento</th><th>Evidencia</th><th>Fecha · Lima</th><th>Ubicación</th>@if(!isset($hideTrip))<th><span class="sr-only">Recorrido</span></th>@endif</tr></thead><tbody>
@foreach($events as $event)<tr><td><strong>{{ $d::type($event->type) }}</strong><details><summary>Datos técnicos</summary><small class="code">{{ $event->type }}</small><code class="uuid">{{ $event->client_event_id }}</code></details></td><td><span class="badge {{ $event->origin === 'SIMULATED' ? 'simulated' : 'sensor' }}">{{ $d::origin($event->origin) }}</span></td><td class="date">{{ $d::date($event->occurred_at) }}</td><td>
@if($event->latitude !== null && $event->longitude !== null && $event->location_origin !== null)
<strong>Ubicación GPS disponible</strong>@if($event->gps_received_at)<small>Recepción GPS · Lima: {{ $d::date($event->gps_received_at) }}</small>@endif<details><summary>Coordenadas y datos GPS</summary><div>Latitud: {{ $event->latitude }}</div><div>Longitud: {{ $event->longitude }}</div>
<small>Origen de ubicación: {{ $event->location_origin }}</small>
@if($event->gps_utc_seconds !== null)<small>Hora GPS UTC (sin fecha): {{ sprintf('%02d:%02d:%02d', intdiv($event->gps_utc_seconds, 3600), intdiv($event->gps_utc_seconds % 3600, 60), $event->gps_utc_seconds % 60) }}</small>@endif
@if($event->gps_age_ms !== null)<small>Antigüedad al crear el evento: {{ $event->gps_age_ms }} ms</small>@endif
</details>
@else
<span class="muted">Ubicación no disponible</span>
@endif
</td>@if(!isset($hideTrip))<td><a class="button quiet" href="{{ route('dashboard.trip', $event->trip->client_trip_id) }}">Ver recorrido</a><details><summary>Datos técnicos</summary><code class="uuid">{{ $event->trip->client_trip_id }}</code></details></td>@endif</tr>@endforeach
</tbody></table></div>@endif
