@extends('dashboard.layout')
@section('title', 'Dispositivos de soporte')
@section('description', 'Consulta la comunicación reciente y abre incidencias por equipo.')
@section('data-notice', '')
@section('page-action')
<a class="button quiet" href="{{ route('dashboard.support') }}">Volver a soporte</a>
@endsection
@section('content')
<form class="panel filters fleet-device-search" action="{{ route('dashboard.support.devices') }}" method="get">
    <div><label for="q">Buscar por nombre o identificador</label><input id="q" name="q" maxlength="100" type="search" value="{{ $search }}"></div>
    <button class="button primary">Buscar</button>
    @if($search)<a href="{{ route('dashboard.support.devices') }}">Limpiar</a>@endif
</form>
<div class="fleet-records">
    @forelse($devices as $device)
        @php($state = $device->communicationState())
        <article class="panel fleet-record">
            <div class="fleet-record-identity">
                <span class="fleet-avatar"><x-nav-icon name="devices"/></span>
                <div><h2>{{ $device->name }}</h2><small>{{ $device->client_device_id }}</small></div>
            </div>
            <div>
                <span class="badge {{ $state === 'Comunicación reciente' ? 'ok' : 'idle' }}">{{ $state }}</span>
                <small>Último reporte recibido · {{ \App\Support\DashboardDisplay::date($device->received_at) }}</small>
                <small>{{ $device->open_tickets_count }} incidencias abiertas o en proceso</small>
            </div>
            <div class="fleet-device-actions">
                <a class="button quiet" href="{{ route('dashboard.support.devices.show', $device->id) }}">Ver ficha</a>
                <a href="{{ route('dashboard.support.create', ['device_id' => $device->id]) }}">Registrar incidencia</a>
            </div>
        </article>
    @empty
        <section class="panel empty fleet-support-empty"><x-nav-icon name="devices"/><h2>{{ $search ? 'No encontramos dispositivos con esa búsqueda' : 'Aún no hay dispositivos registrados' }}</h2></section>
    @endforelse
</div>
@include('dashboard.partials.pagination', ['rows' => $devices])
@endsection
