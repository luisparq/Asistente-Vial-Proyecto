@extends('dashboard.layout')
@section('title', 'Diagnóstico de dispositivos')
@section('description', 'Muestras del ESP32 publicadas por MQTT en modo taller.')
@section('content')
@include('devices.tabs', ['diagnosticsTab' => true])
<p class="notice">Telemetría experimental; no valida el sensor</p>
@if($device)
    <form class="filters panel" method="get" action="{{ route('dashboard.devices.diagnostics') }}">
        <div>
            <label for="device">Dispositivo</label>
            <select id="device" name="device">
                @foreach($devices as $option)
                    <option value="{{ $option->id }}" @selected($option->id === $device->id)>{{ $option->name }} ({{ $option->code }})</option>
                @endforeach
            </select>
        </div>
        <button class="button primary">Cargar dispositivo</button>
    </form>
    @if($session)
        <form class="filters panel" method="get" action="{{ route('dashboard.devices.diagnostics') }}">
            <input type="hidden" name="device" value="{{ $device->id }}">
            <div>
                <label for="session">Sesión</label>
                <select id="session" name="session">
                    @foreach($sessions as $option)
                        <option value="{{ $option->id }}" @selected($option->id === $session->id)>#{{ $option->id }} · {{ \App\Support\DashboardDisplay::date(\Carbon\CarbonImmutable::parse($option->started_at, 'UTC')) }}</option>
                    @endforeach
                </select>
            </div>
            <button class="button primary">Consultar muestras</button>
        </form>
    @endif
    <section class="panel chart">
        <h2>Últimos 30 segundos de la sesión</h2>
        @if($samples->isEmpty())
            <p class="empty">{{ $session ? 'La sesión todavía no tiene muestras.' : 'Este dispositivo todavía no tiene sesiones.' }}</p>
        @else
            <p>Hasta {{ \App\Support\DashboardDisplay::date($end) }} (Lima). El tiempo corresponde a la recepción en el servidor.</p>
            <svg viewBox="0 0 840 240" width="100%" role="img" aria-label="Aceleración cruda ax, ay y az en cuentas de la IMU durante los últimos 30 segundos de la sesión">
                <g fill="none" stroke="currentColor" opacity=".35">
                    <path d="M50 20V200H810 M50 110H810"/>
                </g>
                <g fill="currentColor" font-size="13">
                    <text x="45" y="24" text-anchor="end">{{ $chart['scale'] }}</text>
                    <text x="45" y="114" text-anchor="end">0</text>
                    <text x="45" y="204" text-anchor="end">−{{ $chart['scale'] }}</text>
                    <text x="50" y="224">−30 s</text><text x="810" y="224" text-anchor="end">Última muestra</text>
                </g>
                @foreach(['ax' => '#255cc7', 'ay' => '#b34227', 'az' => '#19774d'] as $axis => $color)
                    @php($points = $chart['series'][$axis])
                    <polyline fill="none" stroke="{{ $color }}" stroke-width="2" points="{{ implode(' ', array_map(fn ($point) => implode(',', $point), $points)) }}"/>
                    @if(count($points) === 1)
                        <circle cx="{{ $points[0][0] }}" cy="{{ $points[0][1] }}" r="3" fill="{{ $color }}"/>
                    @endif
                @endforeach
            </svg>
            <p>Azul: ax · Naranja: ay · Verde: az. Última muestra: ax {{ $samples->last()->ax }}, ay {{ $samples->last()->ay }}, az {{ $samples->last()->az }} cuentas.</p>
        @endif
    </section>
@else
    <p class="empty">Tu empresa todavía no tiene dispositivos asociados a Diagnóstico.</p>
@endif
@endsection
