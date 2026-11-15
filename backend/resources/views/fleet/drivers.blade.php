@extends('dashboard.layout')
@section('title', 'Conductores')
@section('description', 'Gestiona los perfiles, las asignaciones y el historial de conducción de tu empresa.')
@section('data-notice', '')
@section('content')
<div class="fleet-directory">
    <section aria-labelledby="drivers-registered">
        <div class="fleet-directory-heading">
            <h2 id="drivers-registered">Conductores registrados</h2>
            <span>{{ $drivers->total() }} en la empresa</span>
        </div>
        <div class="fleet-records">
            @forelse($drivers as $driver)
                <a class="panel fleet-record" href="{{ route('dashboard.drivers.show', $driver->id) }}">
                    <div class="fleet-record-identity">
                        @include('fleet.driver-avatar')
                        <div>
                            <h3>{{ $driver->full_name }}</h3>
                            <small>{{ $driver->user->email }}</small>
                            <small>Acceso a Driver · {{ ['PENDING' => 'Pendiente', 'ACTIVE' => 'Activo', 'DISABLED' => 'Deshabilitado'][$driver->user->access_status] ?? $driver->user->access_status }}</small>
                        </div>
                    </div>
                    <div class="fleet-record-score">
                        <strong>{{ $driver->score() === null ? 'Sin puntaje' : $driver->score().'/100' }}</strong>
                        <small>{{ $driver->score() === null ? 'Sin actividad en 30 días' : ($driver->score_origin === 'SIMULATED' ? 'Puntaje de prueba' : 'Puntaje provisional').' · 30 días' }}</small>
                    </div>
                    <div class="fleet-record-meta">
                        <span>{{ $driver->status === 'ACTIVE' ? 'Activo' : 'Inactivo' }}</span>
                        <span>{{ $driver->currentAssignment?->vehicle?->plate ?? 'Sin vehículo' }}</span>
                        <span>{{ $driver->documents_count }} documentos · {{ $driver->pending_documents_count }} pendientes</span>
                    </div>
                </a>
            @empty
                <div class="panel empty"><p>Aún no hay conductores registrados.</p></div>
            @endforelse
        </div>
        @include('dashboard.partials.pagination', ['rows' => $drivers])
    </section>
    @include('fleet.registration-card', [
        'image' => 'register-driver', 'heading' => 'Registrar nuevo conductor',
        'description' => 'Crea su cuenta de Driver, adjunta sus documentos y asigna un vehículo desde el mismo formulario.',
        'registerRoute' => 'dashboard.drivers.create', 'buttonLabel' => 'Registrar conductor',
    ])
</div>
@endsection
