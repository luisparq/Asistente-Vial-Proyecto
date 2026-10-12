<!doctype html>
<html lang="es">
<head>
    <meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
    <title>@yield('title') · Asistente Vial</title>
    <link rel="stylesheet" href="{{ asset('css/dashboard.css') }}">
    <link rel="stylesheet" href="{{ asset('css/interface-polish.css') }}">
    @stack('head')
</head>
<body class="fleet-ui {{ request()->routeIs('dashboard.drivers*', 'dashboard.vehicles*') ? 'fleet-page' : '' }}">
<a class="skip" href="#main">Saltar al contenido</a>
<div class="shell">
    <aside class="sidebar">
        <a class="brand" href="{{ route('dashboard.index') }}"><span class="brand-mark" aria-hidden="true">AV</span><span>Asistente Vial<small>Administración de flota</small></span></a>
        <p class="nav-label">ESPACIO DE TRABAJO</p>
        <nav aria-label="Principal">
            @foreach(['index' => ['◫', 'Dashboard'], 'drivers' => ['◉', 'Conductores'], 'vehicles' => ['▣', 'Vehículos'], 'devices' => ['◈', 'Dispositivos'], 'support' => ['◇', 'Soporte técnico'], 'trips' => ['↗', 'Recorridos'], 'events' => ['≋', 'Eventos'], 'map' => ['◎', 'Mapa de incidencias'], 'analytics' => ['▥', 'Analítica'], 'settings' => ['⚙', 'Configuración']] as $route => [$icon, $label])
                @php($active = request()->routeIs('dashboard.'.$route) || ($route === 'trips' && request()->routeIs('dashboard.trip')))
                <a href="{{ route('dashboard.'.$route) }}" @if($active) aria-current="page" @endif><span aria-hidden="true">{{ $icon }}</span>{{ $label }}</a>
            @endforeach
        </nav>
        <div class="sidebar-note"><span class="eyebrow">TECSUP · PROYECTO ACADÉMICO</span><p>Información recibida desde Android.</p><small>La integración física de sensores está pendiente.</small></div>
    </aside>
    <div class="workspace">
        <header class="topbar"><div><span class="eyebrow">EMPRESA</span><strong>{{ auth('web')->user()->company->name }}</strong></div><div class="account"><span>{{ auth('web')->user()->name }}<small>Administrador de flota</small></span><form method="post" action="{{ route('dashboard.logout') }}">@csrf<button class="button quiet">Cerrar sesión</button></form></div></header>
        <main id="main">
            <div class="page-heading"><div><p class="eyebrow">ASISTENTE VIAL INTELIGENTE</p><h1>@yield('title')</h1><p class="subtitle">@yield('description')</p></div><span class="zone">America/Lima · UTC−05:00</span></div>
            <div class="notice"><strong>Datos de prueba</strong><span>Los eventos SIMULATED se generan manualmente. No son detecciones físicas ni infracciones comprobadas.</span></div>
            @if($errors->any())<div class="errors" role="alert"><strong>Revisa los datos introducidos.</strong><ul>@foreach($errors->all() as $error)<li>{{ $error }}</li>@endforeach</ul></div>@endif
            @include('fleet.feedback')
            @yield('content')
            <footer>Asistente Vial Inteligente · Dashboard V1 <span>Solo datos recibidos; la sincronización puede estar pendiente.</span></footer>
        </main>
    </div>
</div>
@stack('scripts')
</body>
</html>
