@if(session('success'))<div class="notice" role="status">{{ session('success') }}</div>@endif
@if(session('activation_code'))
<section class="panel">
@if(session('activation_for_driver'))
<h2>Código de activación</h2><p>Se muestra una sola vez. Válido durante 24 horas y para un solo uso. Entrégalo al conductor de forma privada. Debe introducirlo en la aplicación Asistente Vial Inteligente.</p>
@else
<h2>Código de activación / restablecimiento</h2><p>Se muestra una sola vez. Vence en 24 horas. Entrégalo por un canal privado; no se envió correo. El destinatario lo introduce en <a href="{{ route('access') }}">Activar acceso</a>.</p>
@endif
<code class="activation-code">{{ session('activation_code') }}</code></section>
@endif
