@extends('dashboard.layout')
@section('title', $driver ? 'Editar conductor' : 'Registrar conductor')
@section('data-notice', '')
@section('content')
<form class="panel fleet-form fleet-registration-form" method="post" enctype="multipart/form-data"
      action="{{ $driver ? route('dashboard.drivers.update', $driver->id) : route('dashboard.drivers.store') }}">
    @csrf
    @if($driver) @method('PUT') @endif
    <fieldset>
        <legend>Perfil y acceso</legend>
        <label>Nombre completo<input name="full_name" maxlength="255" required autocomplete="name" value="{{ old('full_name', $driver?->full_name) }}"></label>
        @if(!$driver)
            <label>Correo de acceso<input name="email" type="email" maxlength="255" required autocomplete="email" value="{{ old('email') }}"></label>
            <label>Vehículo inicial (opcional)
                <select name="vehicle_id">
                    <option value="">Sin asignar</option>
                    @foreach($vehicles as $vehicle)
                        <option value="{{ $vehicle->id }}" @selected(old('vehicle_id') == $vehicle->id)>{{ $vehicle->plate }}</option>
                    @endforeach
                </select>
            </label>
        @else
            <label>Estado del perfil
                <select name="status">
                    @foreach(['ACTIVE' => 'Activo', 'INACTIVE' => 'Inactivo'] as $state => $label)
                        <option value="{{ $state }}" @selected(old('status', $driver->status) === $state)>{{ $label }}</option>
                    @endforeach
                </select>
            </label>
            <p>Al inactivar el perfil se finaliza la asignación y se deshabilita el acceso a Driver.</p>
        @endif
        <label>Número de celular (opcional)<input name="phone" type="tel" maxlength="40" autocomplete="tel" value="{{ old('phone', $driver?->phone) }}"></label>
        <label>Dirección (opcional)<input name="address" maxlength="255" autocomplete="street-address" value="{{ old('address', $driver?->address) }}"></label>
        <label>Foto de perfil o tamaño carnet (opcional)<input type="file" name="photo" accept=".jpg,.jpeg,.png"><small>JPG o PNG · hasta 2 MiB</small></label>
    </fieldset>
    <fieldset>
        <legend>Contacto de emergencia (opcional)</legend>
        <label>Nombre<input name="contact_name" maxlength="255" value="{{ old('contact_name', $driver?->emergencyContact?->name) }}"></label>
        <label>Parentesco o relación<input name="contact_relationship" maxlength="100" value="{{ old('contact_relationship', $driver?->emergencyContact?->relationship) }}"></label>
        <label>Teléfono<input name="contact_phone" type="tel" maxlength="40" value="{{ old('contact_phone', $driver?->emergencyContact?->phone) }}"></label>
        <p>Completa los tres campos si deseas guardar un contacto.</p>
    </fieldset>
    <fieldset>
        <legend>Documentación privada (opcional)</legend>
        <p>PDF, JPG o PNG · hasta 5 MiB por documento.</p>
        @foreach($documentLabels as $code => $label)
            <label>{{ $label }}<input type="file" name="documents[{{ $code }}]" accept=".pdf,.jpg,.jpeg,.png"></label>
        @endforeach
    </fieldset>
    <div class="actions">
        <button class="button primary">{{ $driver ? 'Guardar cambios' : 'Registrar conductor' }}</button>
        <a href="{{ $driver ? route('dashboard.drivers.show', $driver->id) : route('dashboard.drivers') }}">Cancelar</a>
    </div>
    @unless($driver)
        <p>Al guardar se genera un código para que el conductor active su cuenta en Driver.</p>
    @endunless
</form>
@endsection
