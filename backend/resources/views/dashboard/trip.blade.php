@extends('dashboard.layout')
@section('title', 'Detalle del recorrido')
@section('description', 'Estado, fechas y eventos recibidos para este recorrido.')
@section('content')
@php($d = \App\Support\DashboardDisplay::class)
<a class="back-link" href="{{ route('dashboard.trips') }}">Volver a recorridos</a>
<section class="panel detail"><h2>Recorrido registrado</h2><details><summary>Identificador técnico</summary><code class="uuid">{{ $trip->client_trip_id }}</code></details><dl class="details">
<div><dt>Estado</dt><dd><span class="badge {{ $trip->status === 'COMPLETED' ? 'completed' : 'active' }}">{{ $d::status($trip->status) }}</span></dd></div><div><dt>Revisión del cliente</dt><dd>{{ $trip->client_revision }}</dd></div>
<div><dt>Inicio · Lima</dt><dd>{{ $d::date($trip->started_at) }}</dd></div><div><dt>Final · Lima</dt><dd>{{ $d::date($trip->ended_at) }}</dd></div>
<div><dt>Usuario que registró</dt><dd>{{ $trip->creator->name }}<small>{{ $trip->creator->email }}</small></dd></div><div><dt>Primera recepción · Lima</dt><dd>{{ $d::date($trip->first_received_at) }}</dd></div><div><dt>Último cambio · Lima</dt><dd>{{ $d::date($trip->last_changed_at) }}</dd></div>
</dl>
@if($trip->ended_at && $trip->ended_at->lt($trip->started_at))<p class="notice">La fecha final es anterior al inicio. Se conservan los valores enviados por el dispositivo.</p>@endif
<p class="context">El usuario que registró el recorrido no equivale a una asignación de conductor. Un estado activo puede estar pendiente de sincronización.</p></section>
<section class="panel"><div class="panel-heading"><h2>{{ $events->total() }} eventos recibidos</h2></div>@include('dashboard.partials.events', ['hideTrip' => true])@include('dashboard.partials.pagination', ['rows' => $events])</section>
@endsection
