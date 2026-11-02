<!doctype html>
<html lang="es"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>Acceso · Asistente Vial</title><link rel="stylesheet" href="{{ asset('css/dashboard.css') }}"><link rel="stylesheet" href="{{ asset('css/interface-polish.css') }}?v=20261029"></head>
<body class="login-page fleet-ui"><main class="login-shell">
    <section class="login-intro"><span class="brand-mark">AV</span><p class="eyebrow">ASISTENTE VIAL INTELIGENTE</p><h1>Una vista clara<br>de los recorridos.</h1><p>Consulta los viajes y eventos recibidos de tu empresa, en un solo lugar.</p><div class="login-note">TECSUP · Arequipa<br>Proyecto académico de asistencia vial</div></section>
    <section class="login-form"><p class="eyebrow">ADMINISTRACIÓN DE FLOTA</p><h2>Bienvenido</h2><p>Ingresa con tu cuenta autorizada de administrador.</p>
        @if($errors->any())<div class="errors" role="alert">@foreach($errors->all() as $error)<p>{{ $error }}</p>@endforeach</div>@endif
        <form method="post" action="{{ route('dashboard.login') }}">@csrf
            <label for="email">Correo electrónico</label><input id="email" name="email" type="email" value="{{ old('email') }}" required maxlength="255" autocomplete="username" autofocus>
            <label for="password">Contraseña</label><input id="password" name="password" type="password" required maxlength="1024" autocomplete="current-password">
            <button class="button primary">Iniciar sesión</button>
        </form><p class="muted">Acceso exclusivo para el rol fleet_admin. Solicita una cuenta al responsable del proyecto.</p><div class="notice compact">Los eventos actuales son simulados y se identifican como datos de prueba.</div>
    </section>
</main></body></html>
