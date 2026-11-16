@php($display = \App\Support\DashboardDisplay::class)
<ul class="fleet-notification-list">
    @foreach($alertRows as $alert)
        <li>
            <div class="fleet-notification-heading">
                <a href="{{ route('dashboard.alerts.show', $alert->client_alert_id) }}">Maniobras repetidas</a>
                @include('dashboard.partials.alert-status')
            </div>
            <small>{{ $alert->vehicle?->plate ?? 'Vehículo sin asignar' }} · {{ $alert->driver?->full_name ?? 'Conductor sin asignar' }}</small>
            <small>{{ $display::date($alert->occurred_at) }} · Lima</small>
        </li>
    @endforeach
</ul>
