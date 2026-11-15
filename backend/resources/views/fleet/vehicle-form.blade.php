@extends('dashboard.layout')
@section('title', $vehicle ? 'Editar vehículo' : 'Registrar vehículo')
@section('data-notice', '')
@section('content')
<form method="post" action="{{ $vehicle ? route('dashboard.vehicles.update', $vehicle->id) : route('dashboard.vehicles.store') }}" class="panel fleet-form fleet-registration-form">
    @csrf
    @if($vehicle) @method('PUT') @endif
    <fieldset>
        <legend>Datos del vehículo</legend>
        <label>Placa<input name="plate" maxlength="30" required value="{{ old('plate', $vehicle?->plate) }}"></label>
        <label>Marca<input name="brand" maxlength="100" required value="{{ old('brand', $vehicle?->brand) }}"></label>
        <label>Modelo<input name="model" maxlength="100" required value="{{ old('model', $vehicle?->model) }}"></label>
        <label>Estado<select name="status">
            @foreach(['ACTIVE' => 'Activo', 'INACTIVE' => 'Inactivo'] as $state => $label)
                <option value="{{ $state }}" @selected(old('status', $vehicle?->status ?? 'ACTIVE') === $state)>{{ $label }}</option>
            @endforeach
        </select></label>
        @unless($vehicle)
            <label>Conductor inicial (opcional)<select name="driver_id">
                <option value="">Sin asignar</option>
                @foreach($drivers as $driver)
                    <option value="{{ $driver->id }}" @selected(old('driver_id') == $driver->id)>{{ $driver->full_name }}</option>
                @endforeach
            </select></label>
        @endunless
    </fieldset>
    <div class="actions">
        <button class="button primary">Guardar vehículo</button>
        <a href="{{ $vehicle ? route('dashboard.vehicles.show', $vehicle->id) : route('dashboard.vehicles') }}">Cancelar</a>
    </div>
</form>
@endsection
