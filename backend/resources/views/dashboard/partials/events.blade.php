@php($d = \App\Support\DashboardDisplay::class)
@if($events->isEmpty())<div class="empty"><x-nav-icon name="events"/><h3>No hay eventos disponibles para este período.</h3><p>Solo se muestran eventos recibidos. No se generan registros para completar esta vista.</p></div>
@else<div class="table-wrap"><table><caption class="sr-only">Eventos recibidos</caption><thead><tr><th>Tipo de incidencia</th><th>Dato registrado</th><th>Fecha y hora · Lima</th>@if(!isset($hideTrip))<th>Recorrido</th><th>Conductor</th><th>Vehículo</th>@endif<th>Ubicación</th></tr></thead><tbody>
@foreach($events as $event)
<tr>
<td><strong>{{ $d::type($event->type) }}</strong><details><summary>Identificador del evento</summary><code class="uuid">{{ $event->client_event_id }}</code></details></td>
<td><span class="badge {{ strtolower($event->origin) }}">{{ $d::origin($event->origin) }}</span></td>
<td class="date">{{ $d::date($event->occurred_at) }}</td>
@if(!isset($hideTrip))
<td>@if($event->trip)<a class="button quiet" href="{{ route('dashboard.trip', $event->trip->client_trip_id) }}">Ver recorrido</a>@endif</td>
<td>{{ $event->trip?->driver?->full_name }}</td>
<td>{{ $event->trip?->vehicle?->plate }}</td>
@endif
<td>
@include('dashboard.partials.event-location')
</td>
</tr>
@endforeach
</tbody></table></div>@endif
