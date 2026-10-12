@extends('platform.layout')
@section('title','Empresas')
@section('content')
<div class="admin-toolbar"><p>Gestiona cada empresa y sus administradores desde una misma ficha.</p><a class="button" href="{{ route('platform.companies.create') }}">Registrar empresa</a></div>
<section class="panel table-wrap"><table><thead><tr><th>Empresa</th><th>Estado</th><th>Administrador responsable</th><th>Usuarios</th><th>Acciones</th></tr></thead><tbody>
@forelse($companies as $company)<tr><td><strong>{{ $company->name }}</strong><small>{{ $company->trade_name ?? 'Nombre comercial no informado' }}</small></td><td><span class="badge">{{ $company->status==='ACTIVE'?'Activa':'Inactiva' }}</span></td><td>{{ $company->administrators->first()?->name ?? 'Sin administrador registrado' }}<small>{{ $company->administrators->first()?->email }}</small></td><td>{{ $company->users_count }}<small>{{ $company->trips_count }} recorridos · {{ $company->events_count }} eventos</small></td><td><div class="actions"><a class="button quiet" href="{{ route('platform.companies.show',$company->id) }}">Ver detalle</a><a class="button quiet" href="{{ route('platform.companies.edit',$company->id) }}">Editar</a><a class="button quiet" href="{{ route('platform.companies.accounts',$company->id) }}">Gestionar accesos</a></div></td></tr>
@empty<tr><td colspan="5">Todavía no hay empresas. Registra una empresa y su primer administrador.</td></tr>@endforelse
</tbody></table>@include('dashboard.partials.pagination',['rows'=>$companies])</section>
@endsection
