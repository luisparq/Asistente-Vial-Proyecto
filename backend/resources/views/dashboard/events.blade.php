@extends('dashboard.layout')
@section('title', 'Eventos')
@section('description', 'Consulta los eventos recibidos y su ubicación cuando está disponible.')
@push('head')
<link rel="stylesheet" href="{{ asset('vendor/maplibre/maplibre-gl.css') }}">

@endpush
@if(isset($alerts))
@section('data-notice', '')
@endif
@section('content')
@php($showAlerts = isset($alerts))
<nav class="map-modes" aria-label="Tipo de registro">
    <a class="button {{ $showAlerts ? '' : 'primary' }}" href="{{ route('dashboard.events', request()->only('from', 'to', 'vehicle_id')) }}" @if(!$showAlerts) aria-current="page" @endif>Maniobras</a>
    <a class="button {{ $showAlerts ? 'primary' : '' }}" href="{{ route('dashboard.events', ['kind' => 'alerts'] + request()->only('from', 'to', 'vehicle_id')) }}" @if($showAlerts) aria-current="page" @endif>Alertas</a>
</nav>
<form id="incident-filters" class="filters panel" method="get" action="{{ route('dashboard.events') }}">
@include('dashboard.partials.dates')
@if($showAlerts)
    <input type="hidden" name="kind" value="alerts">
    @foreach(request()->only('notice_driver', 'notice_vehicle') as $name => $value)
        <input type="hidden" name="{{ $name }}" value="{{ $value }}">
    @endforeach
    <div>
        <label for="alert_status">Estado de revisión</label>
        <select id="alert_status" name="alert_status">
            <option value="">Todas</option>
            <option value="pending" @selected(($filters['alert_status'] ?? null) === 'pending')>Pendientes de revisar</option>
        </select>
    </div>
@endif
@if($vehicles->isNotEmpty())<div><label for="vehicle_id">Vehículo asociado</label><select id="vehicle_id" name="vehicle_id"><option value="">Todos</option>@foreach($vehicles as $vehicle)<option value="{{ $vehicle->id }}" @selected(($filters['vehicle_id'] ?? '') == $vehicle->id)>{{ $vehicle->plate }}</option>@endforeach</select></div>@else<p class="muted">No hay vehículos asociados a eventos georreferenciados.</p>@endif
@if(!$showAlerts && !empty($filters['origin']))<input type="hidden" name="origin" value="{{ $filters['origin'] }}">@endif
<button class="button primary">Aplicar filtros</button><a href="{{ route('dashboard.events', $showAlerts ? ['kind' => 'alerts'] : []) }}">Limpiar</a></form>
<div class="fleet-events-grid">
    <section class="panel" aria-labelledby="event-list-title">
        @if($showAlerts)
            <div class="panel-heading"><div><h2 id="event-list-title">Alertas</h2><p>{{ $alerts->total() }} alertas encontradas</p></div></div>
            @if($alerts->isEmpty())<p class="empty">No hay alertas para estos filtros.</p>@endif
            @include('dashboard.partials.alert-list', ['alertRows' => $alerts])
            @include('dashboard.partials.pagination', ['rows' => $alerts])
        @else
        <div class="panel-heading"><div><h2 id="event-list-title">Últimos eventos</h2><p>{{ $events->total() }} eventos encontrados</p></div></div>
        @include('dashboard.partials.event-list')
        @include('dashboard.partials.pagination', ['rows' => $events])
        @endif
    </section>
<section class="panel incident-panel" id="mapa-incidencias">
<div class="panel-heading"><div><h2>Mapa de incidencias</h2><p>{{ count($geojson['features']) }} {{ $showAlerts ? (count($geojson['features']) === 1 ? 'alerta' : 'alertas') : (count($geojson['features']) === 1 ? 'evento' : 'eventos') }} con ubicación</p></div><div class="map-modes" role="group" aria-label="Visualización del mapa"><button id="mode-markers" type="button" class="button primary" aria-pressed="true" disabled>Marcadores</button><button id="mode-heat" type="button" class="button" aria-pressed="false" disabled>Mapa de calor</button></div></div>
<div class="map-legend"><span>{{ $showAlerts ? 'Marcadores naranjas: alertas de prueba' : 'Dato de prueba' }}</span><span id="heat-legend" hidden>Menor concentración <i class="heat-scale"></i> Mayor concentración<small>El color indica cuántos registros se concentran en el área visible. La validación del riesgo vial sigue pendiente.</small></span></div>
<div class="incident-cartography incident-controls">
    @unless($showAlerts)
    <div>
        <label for="type">Tipo de maniobra</label>
        <select id="type" name="type" form="incident-filters">
            <option value="">Todas las maniobras</option>
            @foreach(\App\Domain\DrivingEventType::cases() as $type)
                <option value="{{ $type->value }}" @selected(($filters['type'] ?? '') === $type->value)>{{ \App\Support\DashboardDisplay::type($type->value) }}</option>
            @endforeach
        </select>
    </div>
    <button class="button quiet" type="submit" form="incident-filters" formaction="{{ route('dashboard.events') }}#mapa-incidencias">Filtrar por tipo</button>
    @endunless
    <div><label for="incident-style">Cartografía</label><select id="incident-style" disabled>
        <option value="light">Clara</option><option value="dark">Oscura</option>
    </select></div>
</div>
<p id="map-status" role="status">Cargando cartografía…</p>
@if(isset($focusedAlert))
    <p>{{ \App\Support\DashboardDisplay::date($focusedAlert->occurred_at) }} · Lima</p>
@endif
@if(count($geojson['features']) === 0)
    <p class="empty">{{ isset($focusedAlert) ? 'Ubicación no disponible' : ($showAlerts ? 'No hay alertas con ubicación para estos filtros.' : 'No hay eventos con ubicación para estos filtros.') }}</p>
@endif
<div class="incident-grid"><div id="incident-map" data-focus="{{ $focusedAlert->client_alert_id ?? '' }}" data-light="{{ $mapStyle }}" data-dark="{{ config('maps.dark_style_url') }}" aria-label="Mapa interactivo de eventos"></div><aside id="map-details" class="map-details" aria-live="polite"><h3>{{ $showAlerts ? 'Detalle de la alerta' : 'Detalle del evento' }}</h3><p>Selecciona un punto del mapa para ver su detalle.</p><div id="event-detail"></div></aside></div>
</section>
</div>
@endsection
@push('scripts')
<script type="application/json" id="incident-data">{!! json_encode($geojson, JSON_HEX_TAG | JSON_HEX_AMP | JSON_HEX_APOS | JSON_HEX_QUOT | JSON_THROW_ON_ERROR) !!}</script>
<script src="{{ asset('vendor/maplibre/maplibre-gl.js') }}" defer></script>

<script src="{{ asset('js/incident-map.js') }}" defer></script>
@endpush
