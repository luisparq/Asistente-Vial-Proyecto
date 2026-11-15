<!doctype html>
<html lang="es">
<head>
    <meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
    <title>@yield('title') · AVIGEM Fleet</title>
    <script src="{{ asset('js/fleet-theme.js') }}" nonce="{{ request()->attributes->get('fleet_script_nonce') }}"></script>
    <link rel="icon" href="{{ asset('favicon.svg') }}" type="image/svg+xml">
    <link rel="stylesheet" href="{{ asset('css/avigem-tokens.css') }}">
    <link rel="stylesheet" href="{{ asset('css/avigem.css') }}">
    @stack('head')
</head>
<body class="fleet-ui {{ request()->routeIs('dashboard.drivers*', 'dashboard.vehicles*') ? 'fleet-page' : '' }} {{ request()->routeIs('dashboard.support.*') ? 'fleet-support-flow' : '' }}">
<a class="skip" href="#main">Saltar al contenido</a>
@php
    $sections = [
        'Operaciones' => ['index' => 'Resumen', 'trips' => 'Recorridos', 'live-map' => 'Mapa en vivo', 'events' => 'Eventos'],
        'Gestión de flota' => ['vehicles' => 'Vehículos', 'drivers' => 'Conductores', 'devices' => 'Dispositivos'],
        'Soporte' => ['support' => 'Soporte técnico', 'support.devices' => 'Dispositivos de soporte', 'settings' => 'Configuración'],
    ];
    $currentPage = match (true) {
        request()->routeIs('dashboard.trip') => 'trips',
        request()->routeIs('dashboard.map') => 'events',
        request()->routeIs('dashboard.analytics') => 'index',
        request()->routeIs('dashboard.support.devices*') => 'support.devices',
        default => explode('.', request()->route()->getName())[1],
    };
    $currentSection = collect($sections)->search(fn ($links) => isset($links[$currentPage]));
@endphp
<header class="fleet-header">
    <div class="fleet-header-inner">
        <div class="fleet-masthead">
            <a class="brand" href="{{ route('dashboard.index') }}"><span class="brand-mark"><x-avigem-mark :size="26"/></span><span>AVIGEM<small>Fleet</small></span></a>
            <div class="fleet-profile">
                <div class="fleet-company">
                    <strong title="{{ auth('web')->user()->company->name }}">{{ auth('web')->user()->company->name }}</strong>
                    <small title="{{ auth('web')->user()->name }}">{{ auth('web')->user()->name }}</small>
                </div>
                <form method="post" action="{{ route('dashboard.logout') }}">@csrf<button class="button quiet">Cerrar sesión</button></form>
            </div>
            <nav class="fleet-sections" aria-label="Áreas de Fleet">
                @foreach($sections as $section => $links)
                    @if($section === $currentSection)
                        <span aria-current="true">{{ $section }}</span>
                    @else
                        <a href="{{ route('dashboard.'.array_key_first($links)) }}">{{ $section }}</a>
                    @endif
                @endforeach
            </nav>
        </div>
    </div>
</header>
<div class="fleet-workspace">
    <div class="fleet-rail">
        <div class="fleet-theme-switch" role="group" aria-label="Tema de Fleet" hidden>
            <button type="button" data-fleet-theme="light" aria-label="Modo claro" title="Modo claro" aria-pressed="true">
                <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="4"/><path d="M12 2v2m0 16v2M2 12h2m16 0h2M5 5l1.5 1.5m11 11L19 19M5 19l1.5-1.5m11-11L19 5"/></svg>
            </button>
            <button type="button" data-fleet-theme="dark" aria-label="Modo oscuro" title="Modo oscuro" aria-pressed="false">
                <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M21 13.4A9 9 0 0 1 10.6 3 9 9 0 1 0 21 13.4Z"/></svg>
            </button>
        </div>
        <nav class="fleet-subnav" aria-label="{{ $currentSection }}">
            @foreach($sections[$currentSection] ?? [] as $route => $label)
                <a href="{{ route('dashboard.'.$route) }}" @if($route === $currentPage) aria-current="{{ request()->routeIs('dashboard.'.$route) ? 'page' : 'location' }}" @endif><x-nav-icon :name="$route === 'support.devices' ? 'devices' : $route"/><span>{{ $label }}</span></a>
            @endforeach
        </nav>
    </div>
<main id="main" tabindex="-1">
    <div class="page-heading"><div><h1>@yield('title')</h1><p class="subtitle">@yield('description')</p></div>@yield('page-action')</div>
    @section('data-notice')
        <div class="notice"><strong>Dato de prueba</strong><span>Los eventos de prueba se generan manualmente. La validación física sigue pendiente.</span></div>
    @show
    @if($errors->any())<div class="errors" role="alert"><strong>Revisa los datos introducidos.</strong><ul>@foreach($errors->all() as $error)<li>{{ $error }}</li>@endforeach</ul></div>@endif
    @include('fleet.feedback')
    @yield('content')
    <footer><span>AVIGEM — un producto de Vialigent Technologies</span><span>Solo datos recibidos; la sincronización puede estar pendiente. Proyecto académico · TECSUP</span></footer>
</main>
</div>
@stack('scripts')
</body>
</html>
