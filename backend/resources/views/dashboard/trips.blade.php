@extends('dashboard.layout')
@section('title', 'Recorridos')
@section('description', 'Consulta el historial recibido y su estado registrado.')
@section('content')
<form class="filters panel" method="get" action="{{ route('dashboard.trips') }}">
<div><label for="status">Estado</label><select name="status" id="status"><option value="">Todos</option>@foreach(\App\Domain\TripStatus::cases() as $status)<option value="{{ $status->value }}" @selected(request('status') === $status->value)>{{ \App\Support\DashboardDisplay::status($status->value) }}</option>@endforeach</select></div>
@include('dashboard.partials.dates')
<div><label for="user_id">Usuario que registró</label><select id="user_id" name="user_id"><option value="">Todos</option>@foreach($users as $user)<option value="{{ $user->id }}" @selected((string)request('user_id') === (string)$user->id)>{{ $user->name }}</option>@endforeach</select></div>
<button class="button primary">Aplicar filtros</button><a href="{{ route('dashboard.trips') }}">Limpiar</a></form>
<section class="panel"><div class="panel-heading"><h2>{{ $trips->total() }} recorridos encontrados</h2></div>@include('dashboard.partials.trips')@include('dashboard.partials.pagination', ['rows' => $trips])</section>
@endsection
