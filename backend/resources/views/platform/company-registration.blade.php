@extends('platform.layout')
@section('title','Registrar empresa')
@section('content')
<nav class="admin-steps" aria-label="Pasos del registro">@foreach([1=>'Empresa',2=>'Responsable',3=>'Configuración',4=>'Confirmación'] as $n=>$label)<span @if($step===$n) aria-current="step" @endif>{{ $n }}. {{ $label }}</span>@endforeach</nav>
<section class="panel admin-registration"><p class="eyebrow">PASO {{ $step }} DE 4</p><h2>{{ [1=>'Datos de la empresa',2=>'Responsable empresarial',3=>'Configuración inicial',4=>'Revisar y crear'][$step] }}</h2>
<form method="post" action="{{ $step===4 ? route('platform.companies.store') : route('platform.companies.registration') }}" class="fleet-form">
@csrf<input type="hidden" name="submission_token" value="{{ $token }}"><input type="hidden" name="step" value="{{ $step }}">
@if($step===1)
<p>Utiliza los datos reales disponibles. El nombre comercial es opcional.</p>
<label>Razón social<input name="name" required maxlength="255" autocomplete="organization" value="{{ old('name',$draft['name']??'') }}"></label>
<label>Nombre comercial <small>Opcional</small><input name="trade_name" maxlength="255" value="{{ old('trade_name',$draft['trade_name']??'') }}"></label>
@elseif($step===2)
<p>Se creará una cuenta de administrador empresarial (fleet_admin). Su titular establecerá la contraseña con un código privado de activación.</p>
<label>Nombre del responsable<input name="admin_name" required maxlength="255" autocomplete="name" value="{{ old('admin_name',$draft['admin_name']??'') }}"></label>
<label>Correo del responsable<input name="admin_email" type="email" required maxlength="255" autocomplete="email" value="{{ old('admin_email',$draft['admin_email']??'') }}"></label>
@elseif($step===3)
<label>Estado inicial<select name="status"><option value="ACTIVE" @selected(old('status',$draft['status']??'ACTIVE')==='ACTIVE')>Activa</option><option value="INACTIVE" @selected(old('status',$draft['status']??'ACTIVE')==='INACTIVE')>Inactiva</option></select></label>
<p>Una empresa inactiva conserva sus datos, pero sus cuentas no pueden activar ni iniciar sesión hasta que se habilite.</p>
<p>El administrador comienza pendiente de activación. El código vence en 24 horas y admite un solo uso. No se envía correo automáticamente.</p>
@else
<dl class="admin-summary"><dt>Razón social</dt><dd>{{ $draft['name'] }}</dd><dt>Nombre comercial</dt><dd>{{ $draft['trade_name']??'No informado' }}</dd><dt>Responsable</dt><dd>{{ $draft['admin_name'] }}<br>{{ $draft['admin_email'] }}</dd><dt>Empresa</dt><dd>{{ $draft['status']==='ACTIVE'?'Activa':'Inactiva' }}</dd><dt>Cuenta</dt><dd>Administrador empresarial · Pendiente de activación</dd></dl>
<p>Al confirmar se crearán empresa, cuenta e invitación en una sola operación. Todavía no se han guardado registros.</p>
@endif
<div class="actions">@if($step>1)<a class="button quiet" href="{{ route('platform.companies.create',['submission_token'=>$token,'step'=>$step-1]) }}">Anterior</a>@endif<button class="button">{{ $step===4?'Crear empresa y administrador':'Continuar' }}</button><a href="{{ route('platform.companies') }}">Salir del registro</a></div>
</form></section>
@endsection
