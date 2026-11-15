@extends('dashboard.layout')
@section('title', 'Mapa en vivo')
@section('data-notice', '')

@push('head')
<link rel="stylesheet" href="{{ asset('vendor/maplibre/maplibre-gl.css') }}">
<link rel="stylesheet" href="{{ asset('css/live-map.css') }}">
@endpush

@section('content')
<div id="live-map" data-url="{{ route('dashboard.live-map.vehicles') }}"
    data-light="{{ config('maps.style_url') }}" data-dark="{{ config('maps.dark_style_url') }}"
    data-recent="{{ config('maps.live_recent_seconds') }}" data-poll="{{ config('maps.live_poll_seconds') }}">
    <div class="panel live-map-toolbar">
        <label>Buscar vehículo o conductor<input id="live-search" type="search" placeholder="Vehículo, matrícula o conductor"></label>
        <label>Estado GPS<select id="live-filter">
            <option value="">Todos</option><option value="recent">Ubicación reciente</option>
            <option value="stale">Ubicación desactualizada</option><option value="missing">Sin ubicación disponible</option>
        </select></label>
        <label>Cartografía<select id="live-style" disabled>
            <option value="light">Clara</option><option value="dark">Oscura</option>
        </select></label>
        <button id="live-fit" type="button" class="button" disabled>Centrar vehículos</button>
    </div>
    <fieldset id="live-road-catalog" class="incident-catalog" data-url="{{ route('road-catalog', [], false) }}" disabled>
        <legend>Capas viales de Arequipa</legend>
        <label><input type="checkbox" value="speed"> <span class="catalog-dot speed"></span> Límites de velocidad</label>
        <label><input type="checkbox" value="schools"> <span class="catalog-dot schools"></span> Colegios</label>
        <label><input type="checkbox" value="traffic"> <span class="catalog-dot traffic"></span> Tráfico reciente</label>
        <p  role="status">Activa una capa para consultar el catálogo vial.</p>
    </fieldset>
    <div class="live-map-grid">
        <section class="panel live-map-canvas" aria-label="Mapa de vehículos">
            <div id="live-canvas" aria-label="Cartografía interactiva"></div>
            <p id="live-map-message" class="live-map-message" role="status">Cargando cartografía…</p>
            <div class="live-map-legend">
                <span class="live-gps-recent">GPS reciente</span><span class="live-gps-stale">GPS desactualizado</span>
            </div>
        </section>
        <aside class="panel live-map-fleet" aria-labelledby="live-fleet-title">
            <div class="panel-heading"><div><h2 id="live-fleet-title">Vehículos</h2>
                <p id="live-updated" role="status">Cargando flota…</p></div></div>
            <p id="live-empty" class="muted" hidden></p>
            <ul id="live-vehicles"></ul>
        </aside>
    </div>
    <noscript><p class="notice">Activa JavaScript para consultar el mapa y los vehículos.</p></noscript>
    <template id="live-vehicle-card">
        <li><button type="button" class="live-vehicle-card" aria-pressed="false">
            <span class="live-vehicle-title"><x-nav-icon name="vehicles"/><strong data-field="name"></strong></span>
            <span data-field="plate" class="live-vehicle-plate"></span>
            <span class="live-vehicle-driver"><x-nav-icon name="profile"/><span data-field="driver"></span></span>
            <span class="muted">Actividad no confirmada</span>
            <span data-field="gps"></span>
            <span data-field="received" class="muted"></span>
            <span data-field="measured" class="muted"></span>
            <span data-field="action"></span>
        </button></li>
    </template>
</div>
@endsection

@push('scripts')
<script src="{{ asset('vendor/maplibre/maplibre-gl.js') }}" defer></script>
<script src="{{ asset('js/road-catalog-map.js') }}" defer></script>
<script src="{{ asset('js/live-map.js') }}" defer></script>
@endpush
