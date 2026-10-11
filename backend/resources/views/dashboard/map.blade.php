@extends('dashboard.layout')
@section('title', 'Mapa de incidencias')
@section('description', 'Eventos georreferenciados de tu empresa. Sin seguimiento en tiempo real.')
@push('head')
<link rel="stylesheet" href="{{ asset('vendor/maplibre/maplibre-gl.css') }}">
<link rel="stylesheet" href="{{ asset('css/incident-map.css') }}">
@endpush
@section('content')
<form class="filters panel" method="get" action="{{ route('dashboard.map') }}">
@include('dashboard.partials.dates')
<div><label for="type">Tipo de maniobra</label><select id="type" name="type"><option value="">Todos</option>@foreach(\App\Domain\DrivingEventType::cases() as $type)<option value="{{ $type->value }}" @selected(($filters['type'] ?? '') === $type->value)>{{ \App\Support\DashboardDisplay::type($type->value) }}</option>@endforeach</select></div>
<div><label for="origin">Origen del evento</label><select id="origin" name="origin"><option value="">Todos</option><option value="SIMULATED" @selected(($filters['origin'] ?? '') === 'SIMULATED')>SIMULATED · prueba</option><option value="SENSOR" @selected(($filters['origin'] ?? '') === 'SENSOR')>SENSOR · reservado, no validado</option></select></div>
@if($vehicles->isNotEmpty())<div><label for="vehicle_id">Vehículo asociado</label><select id="vehicle_id" name="vehicle_id"><option value="">Todos</option>@foreach($vehicles as $vehicle)<option value="{{ $vehicle->id }}" @selected(($filters['vehicle_id'] ?? '') == $vehicle->id)>{{ $vehicle->plate }}</option>@endforeach</select></div>@else<p class="muted">No hay vehículos asociados a eventos georreferenciados.</p>@endif
<button class="button primary">Aplicar filtros</button><a href="{{ route('dashboard.map') }}">Limpiar</a></form>
<section class="panel incident-panel">
<div class="panel-heading"><div><h2>{{ count($geojson['features']) }} {{ count($geojson['features']) === 1 ? 'evento' : 'eventos' }} con ubicación</h2><p>Solo coordenadas válidas; registros sin ubicación excluidos.</p></div><div class="map-modes" role="group" aria-label="Visualización del mapa"><button id="mode-markers" type="button" class="button primary" aria-pressed="true" disabled>Marcadores</button><button id="mode-heat" type="button" class="button" aria-pressed="false" disabled>Mapa de calor</button></div></div>
<div class="map-legend"><span><i class="legend-dot simulated"></i> SIMULATED · prueba</span><span><i class="legend-dot sensor"></i> SENSOR · futuro, no validado</span><span id="heat-legend" hidden>Menor concentración <i class="heat-scale"></i> Mayor concentración</span></div>
<p class="context">La intensidad indica concentración relativa de registros al zoom actual, no riesgo vial demostrado. Incluso un solo evento produce una señal de calor. Los datos de prueba no acreditan zonas peligrosas.</p>
<p id="map-status" role="status">Cargando cartografía…</p>
@if(count($geojson['features']) === 0)<p class="empty">No hay eventos con ubicación para estos filtros.</p>@endif
<div class="incident-grid"><div id="incident-map" data-style="{{ $mapStyle }}" aria-label="Mapa interactivo de eventos"></div><aside id="map-details" class="map-details" aria-live="polite"><h3>Detalle del evento</h3><p>Selecciona un punto o un registro.</p><label for="event-select">Registro recibido</label><select id="event-select"><option value="">Seleccionar evento</option>@foreach($geojson['features'] as $feature)<option value="{{ $loop->index }}">{{ $feature['properties']['date'] }} · {{ $feature['properties']['label'] }}</option>@endforeach</select><div id="event-detail"></div></aside></div>
</section>
@endSection
@push('scripts')
<script type="application/json" id="incident-data">{!! json_encode($geojson, JSON_HEX_TAG | JSON_HEX_AMP | JSON_HEX_APOS | JSON_HEX_QUOT | JSON_THROW_ON_ERROR) !!}</script>
<script src="{{ asset('vendor/maplibre/maplibre-gl.js') }}" defer></script>
<script src="{{ asset('js/incident-map.js') }}" defer></script>
@endpush
