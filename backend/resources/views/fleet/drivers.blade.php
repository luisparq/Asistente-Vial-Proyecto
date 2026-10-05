@extends('dashboard.layout')
@section('title','Conductores')
@section('description','Perfiles operativos y acceso de tu empresa.')
@section('content')
<p><a class="button" href="{{ route('dashboard.drivers.create') }}">Registrar conductor</a></p>
<section class="panel table-wrap"><table><thead><tr><th>Nombre</th><th>Cuenta</th><th>Estado</th><th>Vehículo actual</th><th>Documentos</th></tr></thead><tbody>
@forelse($drivers as $driver)<tr><td><a href="{{ route('dashboard.drivers.show',$driver->id) }}">{{ $driver->full_name }}</a></td><td>{{ $driver->user->email }}<small>Acceso a la app: {{ ['PENDING'=>'Pendiente de activación','ACTIVE'=>'Activo','DISABLED'=>'Deshabilitado'][$driver->user->access_status] ?? $driver->user->access_status }}</small></td><td>{{ $driver->status === 'ACTIVE' ? 'Activo' : 'Inactivo' }}</td><td>{{ $driver->currentAssignment?->vehicle->plate ?? 'Sin asignación' }}</td><td>{{ $driver->documents->count() }} registrados · {{ $driver->documents->where('review_status','PENDING')->count() }} pendientes</td></tr>
@empty<tr><td colspan="5">No hay conductores registrados.</td></tr>@endforelse
</tbody></table>@include('dashboard.partials.pagination',['rows'=>$drivers])</section>
@endsection
