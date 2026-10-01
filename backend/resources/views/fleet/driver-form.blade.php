@extends('dashboard.layout')
@section('title',$driver?'Editar conductor':'Registrar conductor')
@section('content')
<form class="panel fleet-form" method="post" action="{{ $driver ? route('dashboard.drivers.update',$driver->id) : route('dashboard.drivers.store') }}">@csrf
@if($driver)@method('PUT')@endif
<label>Nombre completo<input name="full_name" maxlength="255" required value="{{ old('full_name',$driver?->full_name) }}"></label>
@if(!$driver)<label>Email de acceso<input name="email" type="email" maxlength="255" required value="{{ old('email') }}"></label><p>La cuenta será DRIVER, pendiente de activación. El conductor elegirá su contraseña.</p>
@else<label>Estado del perfil<select name="status">@foreach(['ACTIVE','INACTIVE'] as $state)<option @selected(old('status',$driver->status)===$state)>{{ $state }}</option>@endforeach</select></label><p>Inactivar finaliza la asignación y deshabilita el acceso. Reactivar el perfil no reactiva automáticamente la cuenta.</p>@endif
<fieldset><legend>Contacto de emergencia (opcional)</legend><label>Nombre<input name="contact_name" maxlength="255" value="{{ old('contact_name',$driver?->emergencyContact?->name) }}"></label><label>Relación<input name="contact_relationship" maxlength="100" value="{{ old('contact_relationship',$driver?->emergencyContact?->relationship) }}"></label><label>Teléfono<input name="contact_phone" type="tel" maxlength="40" value="{{ old('contact_phone',$driver?->emergencyContact?->phone) }}"></label><p>Completa los tres campos o déjalos vacíos. Vaciar los tres elimina el contacto actual.</p></fieldset>
@if(!$driver)<label>Vehículo inicial (opcional)<select name="vehicle_id"><option value="">Sin asignar</option>@foreach($vehicles as $vehicle)<option value="{{ $vehicle->id }}" @selected(old('vehicle_id')==$vehicle->id)>{{ $vehicle->plate }}</option>@endforeach</select></label><p>Después de guardar podrás adjuntar la documentación privada desde el perfil.</p>@endif
<button class="button">Guardar conductor</button><a href="{{ route('dashboard.drivers') }}">Volver</a></form>
@endsection
