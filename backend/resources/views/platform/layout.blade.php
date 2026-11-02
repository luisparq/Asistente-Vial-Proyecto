<!doctype html>
<html lang="es">
<head>
    <meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
    <title>@yield('title') · Plataforma AV</title>
    <link rel="stylesheet" href="{{ asset('css/dashboard.css') }}">
    <link rel="stylesheet" href="{{ asset('css/admin-registration.css') }}">
    <link rel="stylesheet" href="{{ asset('css/interface-polish.css') }}?v=20261029">
</head>
<body class="fleet-page admin-ui">
<a class="skip" href="#main">Saltar al contenido</a>
<div class="shell">
    <aside class="sidebar">
        <a class="brand" href="{{ route('platform.index') }}"><span class="brand-mark">AV<span class="brand-dot"></span></span><span>Asistente Vial<small>PLATAFORMA</small></span></a>
        <div class="workspace-label"><span class="workspace-symbol">P</span><div>Administración global<small>Espacio de plataforma</small></div></div>
        <p class="nav-label">CENTRO DE CONTROL</p>
        <nav aria-label="Plataforma">
            @foreach(['index'=>'Resumen global','analytics'=>'Análisis global','companies'=>'Empresas','accounts'=>'Cuentas administrativas','profile'=>'Mi perfil','devices'=>'Dispositivos','support'=>'Soporte técnico','reports'=>'Reportes','audits'=>'Auditoría'] as $route=>$label)
                <a href="{{ route('platform.'.$route) }}" @if(request()->routeIs('platform.'.$route)) aria-current="page" @endif><x-nav-icon :name="$route"/><span>{{ $label }}</span></a>
            @endforeach
        </nav>
        <div class="sidebar-note"><span class="eyebrow">ACCESO GLOBAL</span><p>Gestión y soporte de la plataforma.</p><small>Datos personales de uso administrativo.</small></div>
    </aside>
    <div class="workspace">
        <header class="topbar"><div class="breadcrumb"><span class="breadcrumb-mark"></span>Plataforma<span>/</span><strong>@yield('title')</strong></div><div class="account"><span>{{ auth()->user()->name }}<small>Administrador general</small></span><form method="post" action="{{ route('platform.logout') }}">@csrf<button class="button quiet">Cerrar sesión</button></form></div></header>
        <main id="main">
            <div class="page-heading"><div><p class="eyebrow">ADMINISTRACIÓN GLOBAL</p><h1>@yield('title')</h1><p class="subtitle">Gestión de empresas, actividad y soporte en un solo espacio.</p></div><span class="zone">America/Lima · UTC−05:00</span></div>
            <div class="notice"><strong>Entorno experimental</strong><span>SIMULATED = datos de prueba, no infracciones. Fechas America/Lima.</span></div>
            @if($errors->any())<div class="errors" role="alert">@foreach($errors->all() as $error)<p>{{ $error }}</p>@endforeach</div>@endif
            @include('fleet.feedback')
            @yield('content')
            <footer>Asistente Vial Inteligente<span>Administración de plataforma · TECSUP</span></footer>
        </main>
    </div>
</div>
</body></html>
