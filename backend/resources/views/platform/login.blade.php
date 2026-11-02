<!doctype html>
<html lang="es"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Acceso plataforma · Asistente Vial</title><link rel="stylesheet" href="{{ asset('css/dashboard.css') }}"><link rel="stylesheet" href="{{ asset('css/interface-polish.css') }}?v=20261029"></head>
<body class="login-page admin-ui"><main class="login-shell">
    <section class="login-intro"><span class="brand-mark">AV<span class="brand-dot"></span></span><p class="eyebrow">ASISTENTE VIAL / PLATAFORMA</p><h1>Todo el sistema.<br>Una visión clara.</h1><p>Un espacio para gestionar empresas, consultar actividad y acompañar la operación de la plataforma.</p><div class="login-note">Administración global<br>Asistente Vial Inteligente · TECSUP</div></section>
    <section class="login-form"><p class="eyebrow">CENTRO DE CONTROL</p><h2>Acceso a plataforma</h2><p>Ingresa con tu cuenta de administrador general.</p>
        @if($errors->any())<div class="errors" role="alert">@foreach($errors->all() as $error)<p>{{ $error }}</p>@endforeach</div>@endif
        <form method="post" action="{{ route('platform.login.store') }}" class="fleet-form">@csrf
            <label>Correo electrónico<input name="email" type="email" required autocomplete="username"></label>
            <label>Contraseña<input name="password" type="password" required autocomplete="current-password"></label>
            <button class="button primary">Iniciar sesión</button>
        </form><p class="muted">Acceso exclusivo platform_admin. Las cuentas empresariales utilizan su propio panel.</p>
    </section>
</main></body></html>
