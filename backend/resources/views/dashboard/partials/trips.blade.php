@php($d = \App\Support\DashboardDisplay::class)
@php($tripReturn = ['from_view' => request()->routeIs('dashboard.index') ? 'summary' : 'trips', 'source_page' => request('page')] + request()->only('from', 'to', 'status', 'user_id', 'notice_driver', 'notice_vehicle'))
@if($trips->isEmpty())<div class="empty"><x-nav-icon name="trips"/><h3>No hay recorridos registrados todavía.</h3><p>Los recorridos aparecerán cuando Android los sincronice. Si aplicaste filtros, prueba otro período.</p></div>
@else<div class="table-wrap"><table><caption class="sr-only">Recorridos de la empresa</caption><thead><tr><th>Conductor</th><th>Vehículo</th><th>Inicio · Lima</th><th>Fin · Lima</th><th>Duración</th><th>Eventos</th><th>Estado</th><th><span class="sr-only">Acciones</span></th></tr></thead><tbody>
@foreach($trips as $trip)
<tr>
    <td>{{ $trip->driver?->full_name }}</td>
    <td>{{ $trip->vehicle?->plate }}</td>
    <td class="date">{{ $d::date($trip->started_at) }}</td>
    <td class="date">{{ $trip->ended_at ? $d::date($trip->ended_at) : '' }}</td>
    <td>@include('dashboard.partials.trip-duration')</td>
    <td>{{ $trip->events_count }}</td>
    <td><span class="badge {{ $trip->status === 'COMPLETED' ? 'completed' : 'active' }}">{{ $d::status($trip->status) }}</span></td>
    <td><a class="button quiet" href="{{ route('dashboard.trip', ['uuid' => $trip->client_trip_id] + $tripReturn) }}">Ver detalle</a></td>
</tr>
@endforeach
</tbody></table></div>@endif
