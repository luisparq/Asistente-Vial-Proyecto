@extends('dashboard.layout')
@section('title', 'Eventos')
@section('description', 'Explora los registros recibidos por tipo, origen y fecha.')
@section('content')
<form class="filters panel" method="get" action="{{ route('dashboard.events') }}">
<div><label for="type">Tipo</label><select id="type" name="type"><option value="">Todos</option>@foreach(\App\Domain\DrivingEventType::cases() as $type)<option value="{{ $type->value }}" @selected(request('type') === $type->value)>{{ \App\Support\DashboardDisplay::type($type->value) }}</option>@endforeach</select></div>
<div><label for="origin">Origen</label><select id="origin" name="origin"><option value="">Todos</option><option value="SIMULATED" @selected(request('origin') === 'SIMULATED')>Simulado · dato de prueba</option><option value="SENSOR" @selected(request('origin') === 'SENSOR')>SENSOR · reservado</option></select></div>
@include('dashboard.partials.dates')<button class="button primary">Aplicar filtros</button><a href="{{ route('dashboard.events') }}">Limpiar</a></form>
<section class="panel"><div class="panel-heading"><h2>{{ $events->total() }} eventos encontrados</h2></div>@include('dashboard.partials.events')@include('dashboard.partials.pagination', ['rows' => $events])</section>
@endsection
