@php($display = \App\Support\DashboardDisplay::class)
@if($notifications->isEmpty())
    <p class="fleet-notification-empty">Aún no hay maniobras recibidas que coincidan con los filtros.</p>
@else
    <ul class="fleet-notification-list">
        @foreach($notifications as $event)
            <li>
                <div class="fleet-notification-heading">
                    <a href="{{ route('dashboard.trip', ['uuid' => $event->trip->client_trip_id, 'from_view' => 'summary'] + request()->only('from', 'to', 'notice_driver', 'notice_vehicle')) }}">{{ $display::type($event->type) }}</a>
                    @if($event->origin === 'SIMULATED')
                        <span class="badge simulated">Dato de prueba</span>
                    @endif
                </div>
                <small>{{ $event->trip->vehicle?->plate ?? 'Vehículo sin asignar' }} · {{ $event->trip->driver?->full_name ?? 'Conductor sin asignar' }}</small>
                <small>{{ $display::date($event->occurred_at) }} · Lima</small>
            </li>
        @endforeach
    </ul>
@endif
