<section class="panel">
<h2>Calibración del ESP32 · solo Vialigent</h2>
<p>Estas órdenes se dirigen al dispositivo actual, aunque consultes una sesión anterior en los gráficos.</p>
<p>El ESP32 mide, aprende y guarda el perfil en su memoria. Después detecta y hace sonar su buzzer sin necesitar el celular o Internet. Fija el módulo; si cambias de carro o montaje, repite todo el aprendizaje.</p>
@php
$states=['PERSISTENCE_ERROR'=>'No se pudo guardar el perfil en el ESP32; detección desactivada','PROFILE_VERSION_EXHAUSTED'=>'Se agotó el contador de versiones del perfil; requiere mantenimiento','NEEDS_REST'=>'Pendiente de reposo','PROFILE_LOADED'=>'Perfil recuperado de memoria','LEARNING'=>'Midiendo la maniobra normal','REST_READY'=>'Reposo listo','LESSON_READY'=>'Maniobra normal aprendida','PROFILE_APPLIED'=>'Perfil guardado y activado','NEEDS_LEFT_AND_RIGHT_TURNS'=>'Repite giros en ambos sentidos','INSUFFICIENT_OR_INTERRUPTED'=>'Muestras insuficientes o interrumpidas','REST_MOVED'=>'El sensor se movió durante el reposo','INVALID_GRAVITY'=>'Referencia de gravedad no válida','MANEUVER_NOT_OBSERVED'=>'No se observó suficientemente la maniobra','REPEAT_STRAIGHT_ACCELERATION'=>'Repite aceleración suave en recta','DISABLED'=>'Detección desactivada','ENABLED'=>'Detección activada','NEEDS_CALIBRATION'=>'Falta completar la calibración','NEEDS_REST_AND_FORWARD'=>'Primero reposo y aceleración recta','UNKNOWN_COMMAND'=>'Orden desconocida','FINISH_CURRENT_LESSON'=>'Termina la medición en curso','PROFILE_OUT_OF_SENSOR_RANGE'=>'Perfil fuera del rango del sensor'];
$stage=($sensorState['flags'] ?? 0)>>2;
@endphp
<p>Estado reportado por MQTT: <strong>{{ $states[$sensorState['calibrationStatus'] ?? ''] ?? 'Sin estado de control recibido' }}</strong> · {{ [0=>'Sin medición en curso',1=>'Reposo',2=>'Aceleración normal',3=>'Frenada normal',4=>'Giros normales'][$stage] ?? 'Estado no disponible' }}.</p>
@if($deviceState?->mqtt_received_at)<p>Último estado de control recibido: {{ \App\Support\DashboardDisplay::date(\Carbon\CarbonImmutable::parse($deviceState->mqtt_received_at,'UTC')) }}. No indica conexión actual si dejó de llegar telemetría.</p>@endif
<p>Límites reportados: aceleración {{ $sensorState['accelerationLimit'] ?? '—' }} g · frenada {{ $sensorState['brakingLimit'] ?? '—' }} g · giro {{ $sensorState['turnLimit'] ?? '—' }} °/s.</p>
<p>1. Carro detenido y motor apagado: mide reposo 30 s. 2. Aprende aceleración suave en recta. 3. Aprende frenada suave en recta. 4. Aprende giros normales en ambos sentidos. Termina cada maniobra con el botón o espera 60 s. No provoques maniobras bruscas para calibrar; un acompañante opera el panel.</p>
<div class="quick-actions">
@foreach(['rest'=>'1. Calibrar reposo · 30 s','acceleration'=>'2. Aprender aceleración normal','braking'=>'3. Aprender frenada normal','turn'=>'4. Aprender giros normales','finish'=>'Terminar maniobra','buzzer'=>'Comprobar buzzer','enable'=>'Activar perfil','disable'=>'Desactivar detección'] as $op=>$label)
<form method="post" action="{{ route('platform.devices.diagnostics.control') }}">@csrf<input type="hidden" name="device" value="{{ $device->id }}"><input type="hidden" name="operation" value="{{ $op }}"><button class="button">{{ $label }}</button></form>
@endforeach
</div>
<h3>Confirmaciones</h3>
@forelse($commands as $command)<p>{{ $command->operation }} · {{ ['QUEUED'=>'En cola','PUBLISHED'=>'Publicada: esperando ESP32','ACKNOWLEDGED'=>'Recibida por ESP32: revisar estado reportado','EXPIRED'=>'Sin confirmación: orden vencida'][$command->status] }}.</p>@empty<p>Aún no hay órdenes para este dispositivo.</p>@endforelse
<p>Las órdenes requieren MQTT conectado y el proceso mqtt:listen activo. No se confunde publicación con calibración terminada: el perfil está aplicado cuando el dispositivo informa PROFILE_APPLIED.</p>
</section>
