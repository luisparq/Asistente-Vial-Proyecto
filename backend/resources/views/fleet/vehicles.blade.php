@extends('dashboard.layout')
@section('title','Vehículos')
@section('content')
<p><a class="button" href="{{ route('dashboard.vehicles.create') }}">Registrar vehículo</a></p><section class="panel table-wrap"><table><thead><tr><th>Placa</th><th>Marca / modelo</th><th>Estado</th><th>Conductor actual</th></tr></thead><tbody>@forelse($vehicles as $vehicle)<tr><td><a href="{{ route('dashboard.vehicles.show',$vehicle->id) }}">{{ $vehicle->plate }}</a></td><td>{{ $vehicle->brand }} {{ $vehicle->model }}</td><td>{{ $vehicle->status }}</td><td>{{ $vehicle->currentAssignment?->driver->full_name ?? 'Sin asignar' }}</td></tr>@empty<tr><td colspan="4">No hay vehículos registrados.</td></tr>@endforelse</tbody></table>@include('dashboard.partials.pagination',['rows'=>$vehicles])</section>
@endsection
