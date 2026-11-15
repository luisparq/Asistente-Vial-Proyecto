@extends('dashboard.layout')
@section('title', 'Detalle del recorrido')
@section('description', 'Consulta el trazado GPS y los eventos recibidos del viaje.')
@section('data-notice', '')
@section('page-action')
    <a class="button quiet" href="{{ route($returnRoute, $returnFilters) }}">
        <span aria-hidden="true">←</span> {{ $returnLabel }}
    </a>
@endsection
@section('content')
@php
    $d = \App\Support\DashboardDisplay::class;
    $first = $track->first();
    $last = $track->last();
    $arrival = $last && $trip->ended_at && $trip->ended_at->getTimestampMs() - $last['time'] <= 30000;
@endphp
<div class="fleet-trip-grid">
    <section class="panel" aria-labelledby="trip-track-title">
        <div class="panel-heading trip-track-heading">
            <div>
                <h2 id="trip-track-title">Mapa del recorrido</h2>
                <p>{{ $track->count() }} {{ $track->count() === 1 ? 'punto GPS recibido' : 'puntos GPS recibidos' }}</p>
            </div>
            @if($first)<button id="trip-fit" type="button" class="button quiet" disabled>Centrar recorrido</button>@endif
        </div>
        @if($first)
            <div id="trip-track" data-points="{{ $track->toJson() }}" data-arrival="{{ $arrival ? 'true' : 'false' }}"
                data-light="{{ config('maps.style_url') }}" data-dark="{{ config('maps.dark_style_url') }}">
                <div id="trip-map" aria-label="Mapa interactivo del trazado GPS"></div>
                <p id="trip-map-message" class="trip-map-message" role="status">Cargando mapa…</p>
            </div>
            <div class="trip-waypoints">
                @foreach(['Salida GPS' => $first, ($arrival ? 'Llegada GPS' : 'Último punto GPS') => $last] as $label => $point)
                    @continue(!$loop->first && $track->count() < 2)
                    <div>
                        <span class="trip-waypoint-dot {{ $loop->first ? 'start' : 'end' }}" aria-hidden="true"></span>
                        <div><strong>{{ $label }}</strong><small>{{ $d::date(\Carbon\CarbonImmutable::createFromTimestampMsUTC($point['time'])) }} · Lima</small>
                            <small>{{ number_format($point['coordinates'][1], 5, '.', '') }}, {{ number_format($point['coordinates'][0], 5, '.', '') }}</small></div>
                    </div>
                @endforeach
            </div>
            <p class="trip-map-caption">El trazado conecta las muestras recibidas. Los cortes indican intervalos de más de 30 segundos sin puntos GPS.</p>
        @else
            <div class="trip-map-empty">
                <x-nav-icon name="map"/>
                <h3>Este viaje aún no tiene trazado GPS</h3>
                <p>El mapa se dibuja cuando Driver sincroniza posiciones del GPS tomadas durante el recorrido.</p>
            </div>
        @endif
    </section>
    <aside class="panel detail trip-summary" aria-labelledby="trip-summary-title">
        <h2 id="trip-summary-title">Resumen del viaje</h2>
        <span class="badge {{ $trip->status === 'COMPLETED' ? 'completed' : 'active' }}">{{ $d::status($trip->status) }}</span>
        <dl class="details">
            <div><dt>Vehículo</dt><dd>{{ $trip->vehicle?->plate ?? 'Sin asignar' }}</dd></div>
            <div><dt>Conductor</dt><dd>{{ $trip->driver?->full_name ?? 'Sin asignar' }}</dd></div>
            <div><dt>Inicio · Lima</dt><dd>{{ $d::date($trip->started_at) }}</dd></div>
            <div><dt>Fin · Lima</dt><dd>{{ $trip->ended_at ? $d::date($trip->ended_at) : 'Pendiente' }}</dd></div>
            <div><dt>Duración</dt><dd>@if($trip->ended_at) @include('dashboard.partials.trip-duration') @else — @endif</dd></div>
        </dl>
        @if($trip->ended_at && $trip->ended_at->lt($trip->started_at))<p class="notice">La fecha final recibida es anterior al inicio.</p>@endif
        <details class="trip-sync-details">
            <summary>Datos de sincronización</summary>
            <dl class="details">
                <div><dt>Identificador técnico</dt><dd><code class="uuid">{{ $trip->client_trip_id }}</code></dd></div>
                <div><dt>Revisión del cliente</dt><dd>{{ $trip->client_revision }}</dd></div>
                <div><dt>Primera recepción · Lima</dt><dd>{{ $d::date($trip->first_received_at) }}</dd></div>
                <div><dt>Último cambio · Lima</dt><dd>{{ $d::date($trip->last_changed_at) }}</dd></div>
            </dl>
        </details>
    </aside>
</div>
<section class="panel"><div class="panel-heading"><div><h2>Eventos del recorrido</h2><p>{{ $events->total() }} eventos recibidos</p></div></div>@include('dashboard.partials.events', ['hideTrip' => true])@include('dashboard.partials.pagination', ['rows' => $events])</section>
@endsection
@if($track->isNotEmpty())
    @push('head')
    <link rel="stylesheet" href="{{ asset('vendor/maplibre/maplibre-gl.css') }}">
    @endpush
    @push('scripts')
    <script src="{{ asset('vendor/maplibre/maplibre-gl.js') }}" defer></script>
    <script src="{{ asset('js/trip-track.js') }}" defer></script>
    @endpush
@endif
