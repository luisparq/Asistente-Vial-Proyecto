@extends('dashboard.layout')
@section('title',$vehicle->plate)
@section('content')
<section class="panel"><h2>{{ $vehicle->brand }} {{ $vehicle->model }}</h2><p>{{ $vehicle->status }}</p><a class="button quiet" href="{{ route('dashboard.vehicles.edit',$vehicle->id) }}">Editar vehículo</a><h3>Conductor actual</h3><p>{{ $vehicle->currentAssignment?->driver->full_name ?? 'Sin conductor asignado' }}</p><p>Las asignaciones se gestionan desde el perfil del conductor.</p></section>
<section class="panel"><h2>Historial de asignaciones</h2>@forelse($assignments as $assignment)<p><a href="{{ route('dashboard.drivers.show',$assignment->driver_id) }}">{{ $assignment->driver->full_name }}</a> · {{ \App\Support\DashboardDisplay::date($assignment->started_at) }} → {{ $assignment->ended_at?\App\Support\DashboardDisplay::date($assignment->ended_at):'Actual' }}</p>@empty<p>Sin asignaciones.</p>@endforelse @include('dashboard.partials.pagination',['rows'=>$assignments])</section>
<section class="panel"><h2>Recorridos con contexto de vehículo</h2>@forelse($trips as $trip)<p><a href="{{ route('dashboard.trip',$trip->client_trip_id) }}">{{ $trip->client_trip_id }}</a> · {{ $trip->status }}</p>@empty<p>Sin recorridos vinculados. No se atribuyen viajes legacy ni se deduce vehículo al sincronizar.</p>@endforelse @include('dashboard.partials.pagination',['rows'=>$trips])</section>
@endsection
