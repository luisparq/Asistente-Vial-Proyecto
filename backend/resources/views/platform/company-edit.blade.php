@extends('platform.layout')
@section('title','Editar empresa')
@section('content')
<section class="panel"><h2>{{ $company->name }}</h2><form method="post" action="{{ route('platform.companies.update',$company->id) }}" class="fleet-form">@csrf @method('PUT')
<label>Razón social<input name="name" required maxlength="255" value="{{ old('name',$company->name) }}"></label>
<label>Nombre comercial <small>Opcional</small><input name="trade_name" maxlength="255" value="{{ old('trade_name',$company->trade_name) }}"></label>
<div class="actions"><button class="button">Guardar cambios</button><a class="button quiet" href="{{ route('platform.companies.show',$company->id) }}">Cancelar</a></div></form></section>
@endsection
