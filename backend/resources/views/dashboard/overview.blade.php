@extends('dashboard.layout')
@section('title', 'Resumen de flota')
@section('description', 'Recorridos y eventos que tu empresa ha recibido hasta ahora.')
@section('data-notice', '')
@section('content')
<div class="fleet-summary">
    <section class="fleet-indicators" aria-labelledby="fleet-indicators-title">
        <h2 id="fleet-indicators-title">Actividad recibida</h2>
        <dl class="fleet-metrics">
            @foreach($metrics as $label => $value)
                <div>
                    <dt>{{ $label }}</dt>
                    <dd>{{ number_format($value, 0, ',', '.') }}</dd>
                </div>
            @endforeach
            <div>
                <dt><a href="{{ route('dashboard.events', ['kind' => 'alerts', 'alert_status' => 'pending']) }}">Alertas pendientes de revisar</a></dt>
                <dd>{{ number_format($pendingAlerts, 0, ',', '.') }}</dd>
            </div>
        </dl>
    </section>
    <section class="fleet-notifications" id="notificaciones" aria-labelledby="fleet-notifications-title">
        <h2 id="fleet-notifications-title">Notificaciones recientes</h2>
        <form class="fleet-notification-filters" method="get" action="{{ route('dashboard.index') }}#notificaciones">
            @foreach(request()->only('from', 'to') as $name => $value)
                <input type="hidden" name="{{ $name }}" value="{{ $value }}">
            @endforeach
            <div>
                <label for="notice_vehicle">Vehículo</label>
                <select id="notice_vehicle" name="notice_vehicle">
                    <option value="">Todos</option>
                    @foreach($vehicles as $vehicle)
                        <option value="{{ $vehicle->id }}" @selected((string) $vehicle->id === request('notice_vehicle'))>{{ $vehicle->plate }}</option>
                    @endforeach
                </select>
            </div>
            <div>
                <label for="notice_driver">Conductor</label>
                <select id="notice_driver" name="notice_driver">
                    <option value="">Todos</option>
                    @foreach($drivers as $driver)
                        <option value="{{ $driver->id }}" @selected((string) $driver->id === request('notice_driver'))>{{ $driver->full_name }}</option>
                    @endforeach
                </select>
            </div>
            <button class="button">Filtrar</button>
        </form>
        <div id="fleet-notification-feed" aria-live="polite"
             data-refresh-seconds="{{ auth('web')->user()->fleet_notification_interval ?? 15 }}"
             data-url="{{ route('dashboard.notifications', request()->only('from', 'to', 'notice_driver', 'notice_vehicle')) }}">
            @include('dashboard.partials.notifications')
        </div>
    </section>
</div>
@include('dashboard.analytics')
@include('dashboard.partials.driver-leaderboard')
<section class="panel">
    <div class="panel-heading"><div><h2>Últimos recorridos</h2><p>Hasta cinco registros, ordenados por primera recepción.</p></div></div>
    @include('dashboard.partials.trips')
</section>
@endsection

@push('scripts')
@if((int) (auth('web')->user()->fleet_notification_interval ?? 15) > 0)
<script src="{{ asset('js/fleet-notifications.js') }}" nonce="{{ request()->attributes->get('fleet_script_nonce') }}" defer></script>
@endif
@endpush
