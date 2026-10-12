@extends('platform.layout')
@section('title','Mi perfil de plataforma')
@section('content')
<section class="panel"><h2>{{ $user->name }}</h2><dl class="admin-summary"><dt>Correo</dt><dd>{{ $user->email }}</dd><dt>Rol</dt><dd>Administrador general · platform_admin</dd><dt>Empresa</dt><dd>No pertenece a ninguna empresa</dd><dt>Acceso</dt><dd>{{ $user->access_status==='ACTIVE'?'Activo':$user->access_status }}</dd></dl><p>La recuperación de esta cuenta sigue a cargo del operador mediante el mecanismo existente de provisionamiento.</p></section>
@endsection
