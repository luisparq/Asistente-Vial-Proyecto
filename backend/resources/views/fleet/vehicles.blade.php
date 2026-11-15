@extends('dashboard.layout')
@section('title', 'Vehículos')
@section('description', 'Consulta las unidades de tu empresa y sus asignaciones.')
@section('data-notice', '')
@section('content')
<div class="fleet-directory">
    <section aria-labelledby="vehicles-registered">
        <div class="fleet-directory-heading">
            <h2 id="vehicles-registered">Vehículos registrados</h2>
            <span>{{ $vehicles->total() }} en la empresa</span>
        </div>
        <div class="fleet-records">
            @forelse($vehicles as $vehicle)
                <a class="panel fleet-record fleet-vehicle-record" href="{{ route('dashboard.vehicles.show', $vehicle->id) }}">
                    <div class="fleet-record-identity">
                        <span class="fleet-avatar" aria-hidden="true"><x-nav-icon name="vehicles"/></span>
                        <div><h3>{{ $vehicle->plate }}</h3><small>{{ $vehicle->brand }} {{ $vehicle->model }}</small></div>
                    </div>
                    <div class="fleet-record-meta">
                        <span>{{ $vehicle->status === 'ACTIVE' ? 'Activo' : 'Inactivo' }}</span>
                        <span>{{ $vehicle->currentAssignment?->driver?->full_name ?? 'Sin conductor asignado' }}</span>
                    </div>
                </a>
            @empty
                <div class="panel empty"><p>Aún no hay vehículos registrados.</p></div>
            @endforelse
        </div>
        @include('dashboard.partials.pagination', ['rows' => $vehicles])
    </section>
    @include('fleet.registration-card', [
        'image' => 'register-vehicle', 'heading' => 'Registrar nuevo vehículo',
        'description' => 'Agrega una unidad a la flota y selecciona un conductor disponible si ya tienes uno asignado.',
        'registerRoute' => 'dashboard.vehicles.create', 'buttonLabel' => 'Registrar vehículo',
    ])
</div>
@endsection
