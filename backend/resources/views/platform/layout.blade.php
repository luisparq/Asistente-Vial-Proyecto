<!doctype html>
<html lang="es">
<head>
    <meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
    <title>@yield('title') · Vialigent Platform</title>
    <link rel="icon" href="{{ asset('favicon.svg') }}" type="image/svg+xml">
    <link rel="stylesheet" href="{{ asset('css/avigem-tokens.css') }}">
    <link rel="stylesheet" href="{{ asset('css/avigem.css') }}">
</head>
<body class="fleet-page admin-ui">
<a class="skip" href="#main">Saltar al contenido</a>
<div class="shell">
    <aside class="sidebar">
        <a class="brand" href="{{ route('platform.index') }}"><span class="brand-mark"><x-avigem-mark :size="26"/></span><span>Vialigent<small>Platform</small></span></a>
        <nav aria-label="Plataforma">
            @foreach(['index'=>'Resumen global','analytics'=>'Análisis global','companies'=>'Empresas','accounts'=>'Cuentas administrativas','devices'=>'Dispositivos','support'=>'Soporte técnico','reports'=>'Reportes','audits'=>'Auditoría','profile'=>'Mi perfil'] as $route=>$label)
                <a href="{{ route('platform.'.$route) }}" @if(request()->routeIs('platform.'.$route)) aria-current="page" @endif><x-nav-icon :name="$route"/><span>{{ $label }}</span></a>
            @endforeach
        </nav>
        <div class="sidebar-note"><p>Gestión y soporte de la plataforma.</p><small>Datos personales de uso administrativo.</small></div>
    </aside>
    <div class="workspace">
        <header class="topbar"><div class="topbar-context"><strong>Administración global</strong><span class="zone">America/Lima · UTC−05:00</span></div><div class="account"><span>{{ auth()->user()->name }}<small>Administrador general</small></span><form method="post" action="{{ route('platform.logout') }}">@csrf<button class="button quiet">Cerrar sesión</button></form></div></header>
        <main id="main">
            <div class="page-heading"><div><h1>@yield('title')</h1><p class="subtitle">Gestión de empresas, actividad y soporte en un solo espacio.</p></div></div>
            <div class="notice"><strong>Entorno experimental</strong><span>SIMULATED = dato de prueba, no infracciones. Telemetría BLE/MQTT: datos recibidos, no certificados. Fechas America/Lima.</span></div>
            @if($errors->any())<div class="errors" role="alert">@foreach($errors->all() as $error)<p>{{ $error }}</p>@endforeach</div>@endif
            @include('fleet.feedback')
            @yield('content')
            <footer><span>Vialigent Technologies · AVIGEM</span><span>Administración de plataforma · TECSUP</span></footer>
        </main>
    </div>
</div>
</body></html>
