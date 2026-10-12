@extends('platform.layout')
@section('title','Administradores empresariales')
@section('content')
<div class="admin-toolbar"><div><h2>{{ $company->name }}</h2><p>Estas cuentas acceden exclusivamente al dashboard de esta empresa. Los conductores se gestionan desde el dashboard empresarial.</p></div><a class="button quiet" href="{{ route('platform.companies.show',$company->id) }}">Volver a empresa</a></div>
@if($company->status!=='ACTIVE')<div class="notice">Empresa inactiva: sus cuentas no pueden activar ni iniciar sesión.</div>@endif
<section class="panel table-wrap"><table><thead><tr><th>Administrador</th><th>Acceso</th><th>Acción disponible</th></tr></thead><tbody>
@forelse($users as $user)<tr><td>{{ $user->name }}<small>{{ $user->email }}</small></td><td>{{ ['PENDING'=>'Pendiente de activación','ACTIVE'=>'Activo','DISABLED'=>'Deshabilitado'][$user->access_status]??$user->access_status }}</td><td>@include('platform.account-actions')</td></tr>@empty<tr><td colspan="3">Esta empresa aún no tiene administradores.</td></tr>@endforelse
</tbody></table>@include('dashboard.partials.pagination',['rows'=>$users])</section>
@if($company->status==='ACTIVE')<section class="panel"><h2>Añadir administrador</h2><p>Se crea pendiente y se genera un código privado. No se asigna una contraseña visible.</p><form method="post" action="{{ route('platform.companies.accounts.store',$company->id) }}" class="fleet-form">@csrf<label>Nombre<input name="name" required maxlength="255" value="{{ old('name') }}"></label><label>Correo<input name="email" type="email" required maxlength="255" value="{{ old('email') }}"></label><button class="button">Crear cuenta y generar código</button></form></section>@endif
@endsection
