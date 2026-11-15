@extends('dashboard.layout')
@section('title', 'Eventos')
@section('description', 'Consulta los eventos recibidos y su ubicación cuando está disponible.')
@push('head')
<link rel="stylesheet" href="{{ asset('vendor/maplibre/maplibre-gl.css') }}">

@endpush
@section('content')
<form id="incident-filters" class="filters panel" method="get" action="{{ route('dashboard.events') }}">
@include('dashboard.partials.dates')
@if($vehicles->isNotEmpty())<div><label for="vehicle_id">Vehículo asociado</label><select id="vehicle_id" name="vehicle_id"><option value="">Todos</option>@foreach($vehicles as $vehicle)<option value="{{ $vehicle->id }}" @selected(($filters['vehicle_id'] ?? '') == $vehicle->id)>{{ $vehicle->plate }}</option>@endforeach</select></div>@else<p class="muted">No hay vehículos asociados a eventos georreferenciados.</p>@endif
@if(!empty($filters['origin']))<input type="hidden" name="origin" value="{{ $filters['origin'] }}">@endif
<button class="button primary">Aplicar filtros</button><a href="{{ route('dashboard.events') }}">Limpiar</a></form>
<div class="fleet-events-grid">
    <section class="panel" aria-labelledby="event-list-title">
        <div class="panel-heading"><div><h2 id="event-list-title">Últimos eventos</h2><p>{{ $events->total() }} eventos encontrados</p></div></div>
        @include('dashboard.partials.event-list')
        @include('dashboard.partials.pagination', ['rows' => $events])
    </section>
<section class="panel incident-panel" id="mapa-incidencias">
<div class="panel-heading"><div><h2>Mapa de incidencias</h2><p>{{ count($geojson['features']) }} {{ count($geojson['features']) === 1 ? 'evento' : 'eventos' }} con ubicación</p></div><div class="map-modes" role="group" aria-label="Visualización del mapa"><button id="mode-markers" type="button" class="button primary" aria-pressed="true" disabled>Marcadores</button><button id="mode-heat" type="button" class="button" aria-pressed="false" disabled>Mapa de calor</button></div></div>
<div class="map-legend"><span>Dato de prueba</span><span id="heat-legend" hidden>Menor concentración <i class="heat-scale"></i> Mayor concentración<small>El color indica cuántos registros se concentran en el área visible. La validación del riesgo vial sigue pendiente.</small></span></div>
<div class="incident-cartography incident-controls">
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
    <div><label for="incident-style">Cartografía</label><select id="incident-style" disabled>
        <option value="light">Clara</option><option value="dark">Oscura</option>
    </select></div>
</div>
<p id="map-status" role="status">Cargando cartografía…</p>
@if(count($geojson['features']) === 0)<p class="empty">No hay eventos con ubicación para estos filtros.</p>@endif
<div class="incident-grid"><div id="incident-map" data-light="{{ $mapStyle }}" data-dark="{{ config('maps.dark_style_url') }}" aria-label="Mapa interactivo de eventos"></div><aside id="map-details" class="map-details" aria-live="polite"><h3>Detalle del evento</h3><p>Selecciona un punto del mapa para ver su detalle.</p><div id="event-detail"></div></aside></div>
</section>
</div>
@endsection
@push('scripts')
<script type="application/json" id="incident-data">{!! json_encode($geojson, JSON_HEX_TAG | JSON_HEX_AMP | JSON_HEX_APOS | JSON_HEX_QUOT | JSON_THROW_ON_ERROR) !!}</script>
<script src="{{ asset('vendor/maplibre/maplibre-gl.js') }}" defer></script>

<script src="{{ asset('js/incident-map.js') }}" defer></script>
@endpush
