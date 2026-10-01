@extends('dashboard.layout')
@section('title',$vehicle?'Editar vehículo':'Registrar vehículo')
@section('content')
<form method="post" action="{{ $vehicle?route('dashboard.vehicles.update',$vehicle->id):route('dashboard.vehicles.store') }}" class="panel fleet-form">@csrf @if($vehicle)@method('PUT')@endif
<label>Placa<input name="plate" maxlength="30" required value="{{ old('plate',$vehicle?->plate) }}"></label><label>Marca<input name="brand" maxlength="100" required value="{{ old('brand',$vehicle?->brand) }}"></label><label>Modelo<input name="model" maxlength="100" required value="{{ old('model',$vehicle?->model) }}"></label><label>Estado<select name="status">@foreach(['ACTIVE','INACTIVE'] as $state)<option @selected(old('status',$vehicle?->status)===$state)>{{ $state }}</option>@endforeach</select></label><button class="button">Guardar vehículo</button></form>
@endsection
