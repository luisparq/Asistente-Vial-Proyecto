@php($display = \App\Support\DashboardDisplay::class)
<section class="panel fleet-history" id="historial-recorridos">
    <div class="panel-heading"><div><h2>Historial de recorridos</h2><p>{{ $trips->total() }} registros. Los recorridos antiguos se vinculan por la cuenta que los sincronizó.</p></div></div>
    @if($trips->isEmpty())
        <p class="empty">Aún no hay recorridos.</p>
    @else
        <div class="table-wrap">
            <table>
                <thead><tr><th>Inicio · Lima</th><th>Vehículo</th><th>Estado</th><th>Eventos</th><th>Vínculo</th><th>Detalle</th></tr></thead>
                <tbody>
                    @foreach($trips as $trip)
                        <tr>
                            <td class="date">{{ $display::date($trip->started_at) }}</td>
                            <td>{{ $trip->vehicle?->plate ?? '—' }}</td>
                            <td>{{ $display::status($trip->status) }}</td>
                            <td>{{ $trip->events_count }}</td>
                            <td>{{ $trip->driver_id ? 'Conductor confirmado' : 'Sin contexto confirmado' }}</td>
                            <td><a href="{{ route('dashboard.trip', $trip->client_trip_id) }}">Ver recorrido</a></td>
                        </tr>
                    @endforeach
                </tbody>
            </table>
        </div>
        @include('dashboard.partials.pagination', ['rows' => $trips])
    @endif
</section>
<section class="panel fleet-history" id="historial-eventos">
    <div class="panel-heading"><div><h2>Historial de eventos</h2><p>{{ $events->total() }} registros recibidos de Driver.</p></div></div>
    @if($events->isEmpty())
        <p class="empty">Aún no hay eventos.</p>
    @else
        <div class="table-wrap">
            <table>
                <thead><tr><th>Maniobra</th><th>Ocurrencia · Lima</th><th>Origen</th><th>Vínculo</th><th>Detalle</th></tr></thead>
                <tbody>
                    @foreach($events as $event)
                        <tr>
                            <td>{{ $display::type($event->type) }}</td>
                            <td class="date">{{ $display::date($event->occurred_at) }}</td>
                            <td>{{ $display::origin($event->origin) }}</td>
                            <td>{{ $event->trip->driver_id ? 'Conductor confirmado' : 'Sin contexto confirmado' }}</td>
                            <td><a href="{{ route('dashboard.trip', $event->trip->client_trip_id) }}">Ver recorrido</a></td>
                        </tr>
                    @endforeach
                </tbody>
            </table>
        </div>
        @include('dashboard.partials.pagination', ['rows' => $events])
    @endif
</section>
