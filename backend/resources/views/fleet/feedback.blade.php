@if(session('success'))<div class="notice" role="status">{{ session('success') }}</div>@endif
@if(session('activation_code'))
<section class="panel">
@if(session('activation_for_driver'))
<h2>Código de activación</h2><p>Se muestra una sola vez. Válido durante 24 horas y para un solo uso. Entrégalo al conductor de forma privada. Debe introducirlo en la aplicación Asistente Vial Inteligente.</p>
@else
<h2>Código de activación / restablecimiento</h2><p>Se muestra una sola vez. Vence en 24 horas. Entrégalo por un canal privado; no se envió correo. El destinatario lo introduce en <a href="{{ route('access') }}">Activar acceso</a>.</p>
@endif
@php
    $code = session('activation_code');
    $message = session('activation_for_driver')
        ? "AVIGEM: tu código de activación es {$code}. Introdúcelo en la app AVIGEM. Vence en 24 horas y sirve una sola vez. No compartas este código con nadie."
        : 'AVIGEM · Vialigent Technologies: ya tienes acceso a tu empresa. Activa tu cuenta aquí: '.route('access', ['code' => $code])." — Código de verificación: {$code} (vence en 24 horas, un solo uso). No compartas este código con nadie.";
@endphp
<div class="code-row">
<code class="activation-code">{{ $code }}</code>
<a class="button" href="https://wa.me/?text={{ rawurlencode($message) }}" target="_blank" rel="noopener noreferrer">Enviar por WhatsApp</a>
<a class="button quiet" href="sms:?&body={{ rawurlencode($message) }}">Enviar por SMS</a>
</div>
<label>Mensaje listo para copiar<textarea readonly rows="4">{{ $message }}</textarea></label>
<p><small>El mensaje incluye el aviso de no compartir el código. Aun así, envíalo solo a la persona responsable.</small></p></section>
@endif
