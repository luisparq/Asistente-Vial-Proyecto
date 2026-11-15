@php($d = \App\Support\DashboardDisplay::class)
@if($event->latitude !== null && $event->longitude !== null && $event->location_origin !== null)
<strong>Ubicación GPS disponible</strong>@if($event->gps_received_at)<small>Recepción GPS · Lima: {{ $d::date($event->gps_received_at) }}</small>@endif<details><summary>Coordenadas y datos GPS</summary><div>Latitud: {{ $event->latitude }}</div><div>Longitud: {{ $event->longitude }}</div>
<small>Origen de ubicación: {{ $event->location_origin }}</small>
@if($event->gps_utc_seconds !== null)<small>Hora GPS UTC (sin fecha): {{ sprintf('%02d:%02d:%02d', intdiv($event->gps_utc_seconds, 3600), intdiv($event->gps_utc_seconds % 3600, 60), $event->gps_utc_seconds % 60) }}</small>@endif
@if($event->gps_age_ms !== null)<small>Antigüedad al crear el evento: {{ $event->gps_age_ms }} ms</small>@endif
</details>
@else
<span class="muted">Ubicación no disponible</span>
@endif
