<!doctype html>
<html lang="es"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>Acceso · AVIGEM Fleet</title><link rel="icon" href="{{ asset('favicon.svg') }}" type="image/svg+xml"><link rel="stylesheet" href="{{ asset('css/avigem-tokens.css') }}"><link rel="stylesheet" href="{{ asset('css/avigem.css') }}"></head>
<body class="login-page fleet-ui"><main class="login-shell">
    <section class="login-intro"><x-avigem-mark class="login-art" :size="620"/><div class="login-brand"><span class="brand-mark"><x-avigem-mark :size="26"/></span><span>AVIGEM Fleet<small>Gestión empresarial de flotas</small></span></div><h1>Prevención de maniobras de riesgo.</h1><p>AVIGEM es un asistente vial que alerta al conductor durante el viaje y permite a tu equipo revisar los recorridos y eventos de la flota.</p><div class="login-note">AVIGEM — un producto de Vialigent Technologies<br>Proyecto académico · TECSUP · Arequipa</div></section>
    <section class="login-form"><h2>Bienvenido</h2><p>Ingresa con tu cuenta autorizada de administrador.</p>
        @if($errors->any())<div class="errors" role="alert">@foreach($errors->all() as $error)<p>{{ $error }}</p>@endforeach</div>@endif
        <form method="post" action="{{ route('dashboard.login') }}">@csrf
            <label for="email">Correo electrónico</label><input id="email" name="email" type="email" value="{{ old('email') }}" required maxlength="255" autocomplete="username" autofocus>
            <label for="password">Contraseña</label><input id="password" name="password" type="password" required maxlength="1024" autocomplete="current-password">
            <button class="button primary">Iniciar sesión</button>
        </form><p class="muted">Acceso exclusivo para el rol fleet_admin. Solicita una cuenta al responsable del proyecto.</p>
    </section>
</main></body></html>
