@extends('platform.layout')
@section('title','Cuentas administrativas')
@section('content')
<div class="admin-toolbar"><p>Directorio de administradores empresariales. Gestiona sus accesos desde la ficha de su empresa.</p><a class="button quiet" href="{{ route('platform.profile') }}">Mi perfil de plataforma</a></div>
<section class="panel table-wrap"><table><thead><tr><th>Administrador</th><th>Empresa</th><th>Acceso</th><th>Gestión</th></tr></thead><tbody>
@forelse($users as $user)<tr><td>{{ $user->name }}<small>{{ $user->email }}</small></td><td>{{ $user->company->name }}</td><td>{{ ['PENDING'=>'Pendiente de activación','ACTIVE'=>'Activo','DISABLED'=>'Deshabilitado'][$user->access_status]??$user->access_status }}</td><td><a class="button quiet" href="{{ route('platform.companies.accounts',$user->company_id) }}">Gestionar desde empresa</a></td></tr>@empty<tr><td colspan="4">No hay administradores empresariales registrados.</td></tr>@endforelse
</tbody></table>@include('dashboard.partials.pagination',['rows'=>$users])</section><p>Las cuentas de conductores permanecen en sus empresas y no forman parte de este directorio administrativo. El administrador global no pertenece a una empresa.</p>
@endsection
