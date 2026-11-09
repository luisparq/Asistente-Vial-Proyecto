<!doctype html>
<html lang="es">
<head>
    <meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
    <title>@yield('title') · AVIGEM Fleet</title>
    <link rel="icon" href="{{ asset('favicon.svg') }}" type="image/svg+xml">
    <link rel="stylesheet" href="{{ asset('css/avigem-tokens.css') }}">
    <link rel="stylesheet" href="{{ asset('css/avigem.css') }}">
    @stack('head')
</head>
<body class="fleet-ui {{ request()->routeIs('dashboard.drivers*', 'dashboard.vehicles*') ? 'fleet-page' : '' }}">
<a class="skip" href="#main">Saltar al contenido</a>
<div class="shell">
    <aside class="sidebar">
        <a class="brand" href="{{ route('dashboard.index') }}"><span class="brand-mark"><x-avigem-mark :size="26"/></span><span>AVIGEM<small>Fleet</small></span></a>
        <nav aria-label="Principal">
            @foreach(['index' => 'Resumen', 'trips' => 'Recorridos', 'vehicles' => 'Vehículos', 'drivers' => 'Conductores', 'devices' => 'Dispositivos', 'events' => 'Eventos', 'map' => 'Mapa de incidencias', 'analytics' => 'Analítica', 'support' => 'Soporte técnico', 'settings' => 'Configuración'] as $route => $label)
                @php($active = request()->routeIs('dashboard.'.$route) || ($route === 'trips' && request()->routeIs('dashboard.trip')))
                <a href="{{ route('dashboard.'.$route) }}" @if($active) aria-current="page" @endif><x-nav-icon :name="$route"/><span>{{ $label }}</span></a>
            @endforeach
        </nav>
        <div class="sidebar-note"><p>Información recibida desde Android.</p><small>La validación física de detección de maniobras sigue pendiente.</small></div>
    </aside>
    <div class="workspace">
        <header class="topbar"><div class="topbar-context"><strong>{{ auth('web')->user()->company->name }}</strong><span class="zone">America/Lima · UTC−05:00</span></div><div class="account"><span>{{ auth('web')->user()->name }}<small>Administrador de flota</small></span><form method="post" action="{{ route('dashboard.logout') }}">@csrf<button class="button quiet">Cerrar sesión</button></form></div></header>
        <main id="main">
            <div class="page-heading"><div><h1>@yield('title')</h1><p class="subtitle">@yield('description')</p></div></div>
            <div class="notice"><strong>Dato de prueba</strong><span>Los eventos SIMULATED se generan manualmente. No son detecciones físicas ni infracciones comprobadas.</span></div>
            @if($errors->any())<div class="errors" role="alert"><strong>Revisa los datos introducidos.</strong><ul>@foreach($errors->all() as $error)<li>{{ $error }}</li>@endforeach</ul></div>@endif
            @include('fleet.feedback')
            @yield('content')
            <footer><span>AVIGEM — un producto de Vialigent Technologies</span><span>Solo datos recibidos; la sincronización puede estar pendiente. Proyecto académico · TECSUP</span></footer>
        </main>
    </div>
</div>
@stack('scripts')
</body>
</html>
