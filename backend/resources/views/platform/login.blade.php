<!doctype html>
<html lang="es"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Acceso · Vialigent Platform</title><link rel="icon" href="{{ asset('favicon.svg') }}" type="image/svg+xml"><link rel="stylesheet" href="{{ asset('css/avigem-tokens.css') }}"><link rel="stylesheet" href="{{ asset('css/avigem.css') }}"></head>
<body class="login-page admin-ui"><main class="login-shell">
    <section class="login-intro"><x-avigem-mark class="login-art" :size="620"/><div class="login-brand"><span class="brand-mark"><x-avigem-mark :size="26"/></span><span>Vialigent Platform<small>Administración global</small></span></div><h1>Todo el sistema. Una visión clara.</h1><p>Un espacio para gestionar empresas, consultar actividad y acompañar la operación de la plataforma.</p><div class="login-note">AVIGEM — un producto de Vialigent Technologies<br>Administración de plataforma · TECSUP</div></section>
    <section class="login-form"><h2>Acceso a plataforma</h2><p>Ingresa con tu cuenta de administrador general.</p>
        @if($errors->any())<div class="errors" role="alert">@foreach($errors->all() as $error)<p>{{ $error }}</p>@endforeach</div>@endif
        <form method="post" action="{{ route('platform.login.store') }}" class="fleet-form">@csrf
            <label>Correo electrónico<input name="email" type="email" required autocomplete="username"></label>
            <label>Contraseña<input name="password" type="password" required autocomplete="current-password"></label>
            <button class="button primary">Iniciar sesión</button>
        </form><p class="muted">Acceso exclusivo platform_admin. Las cuentas empresariales utilizan su propio panel.</p>
    </section>
</main></body></html>
