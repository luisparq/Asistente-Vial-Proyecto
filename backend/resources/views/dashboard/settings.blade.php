@extends('dashboard.layout')
@section('title', 'Configuración')
@section('description', 'Información de tu cuenta y del espacio de trabajo. Solo lectura.')
@section('content')
<section class="panel detail"><h2>Cuenta y empresa</h2><dl class="details">
<div><dt>Empresa</dt><dd>{{ auth('web')->user()->company->name }}</dd></div><div><dt>Nombre</dt><dd>{{ auth('web')->user()->name }}</dd></div><div><dt>Email</dt><dd>{{ auth('web')->user()->email }}</dd></div><div><dt>Rol</dt><dd>{{ auth('web')->user()->role }}</dd></div><div><dt>Zona de presentación web</dt><dd>America/Lima · UTC−05:00</dd></div></dl></section>
<section class="panel detail"><h2>Alcance actual</h2><p>Consulta de recorridos y eventos recibidos desde Android. Las fechas se guardan e intercambian en UTC.</p><p>La integración física de sensores y GPS está pendiente. Esta versión no incluye mapas, vehículos, conductores ni dispositivos.</p></section>
@endsection
