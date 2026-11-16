@extends('dashboard.layout')
@section('title', 'Alerta por maniobras repetidas')
@section('data-notice', '')
@section('content')
@php($display = \App\Support\DashboardDisplay::class)
<section class="panel">
    <div class="panel-heading">
        <div>
            <h2>Maniobras repetidas</h2>
            <p>{{ $display::date($alert->occurred_at) }} · Lima</p>
        </div>
        @include('dashboard.partials.alert-status')
    </div>
    <dl>
        <dt>Conductor</dt><dd>{{ $alert->driver?->full_name ?? 'Conductor sin asignar' }}</dd>
        <dt>Unidad</dt><dd>{{ $alert->vehicle?->plate ?? 'Vehículo sin asignar' }}</dd>
        @if($canViewContact)
            <dt>Teléfono de contacto de emergencia</dt><dd>{{ $phone ?: 'Contacto no registrado' }}</dd>
        @endif
        <dt>Ubicación</dt>
        <dd>
            @if($alert->location_status === 'AVAILABLE')
                <a href="{{ route('dashboard.events', ['kind' => 'alerts', 'focus_alert' => $alert->client_alert_id]) }}#mapa-incidencias">Ver en mapa</a>
                <small>Ubicación registrada el {{ $display::date($alert->location_captured_at) }} · Lima</small>
            @else
                Ubicación no disponible
            @endif
        </dd>
    </dl>
    @if(!$alert->test_only && !$alert->reviewed_at)
        <form method="post" action="{{ route('dashboard.alerts.review', $alert->client_alert_id) }}">
            @csrf
            @method('PUT')
            <button class="button primary">Marcar como revisada</button>
        </form>
    @endif
</section>
<section class="panel">
    <div class="panel-heading"><h2>Maniobras vinculadas</h2></div>
    <ul class="fleet-notification-list">
        @foreach($events as $event)
            <li>
                <strong>{{ $display::type($event->type) }}</strong>
                <small>{{ $display::date($event->occurred_at) }} · Lima</small>
                <small>{{ $event->client_event_id }}</small>
            </li>
        @endforeach
    </ul>
    <a href="{{ route('dashboard.trip', $alert->trip->client_trip_id) }}">Ver recorrido</a>
</section>
<a href="{{ route('dashboard.events', ['kind' => 'alerts']) }}">Volver a alertas</a>
@endsection
