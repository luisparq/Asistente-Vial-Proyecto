<section class="panel">
<h2>AVIGEM One · {{ $device->name }}</h2>
@if(!$latest || \Carbon\CarbonImmutable::parse($latest->sampled_at,'UTC')->lt(now('UTC')->subSeconds(10)))
<p class="notice">Sin señal reciente. Los valores anteriores no indican el estado actual.</p>
@elseif(($latest->motion_flags & 3)!==3 || $latest->motion_range===4)
<p class="notice">{{ ($latest->motion_flags >> 2)>0 ? 'Calibración en curso' : 'Sin perfil activo de calibración' }}</p>
@else
<p class="notice"><strong>{{ $latest->motion_range===0 ? 'Dentro del rango aprendido' : 'Fuera del rango aprendido' }}</strong></p>
@endif
@if($latest)
<p>@if($latest->mqtt_received_at)Señal recibida desde dispositivo AVIGEM One — MQTT.@endif
@if($latest->ble_received_at) Señal recibida desde celular — {{ $relayOwner?->name ?? 'Conductor asociado' }} · vehículo asociado actualmente {{ $relayOwner?->driver?->currentAssignment?->vehicle?->plate ?? 'sin asignación actual' }}.@endif
@if($latest->mqtt_received_at && $latest->ble_received_at)<strong>Ambos caminos: una sola muestra registrada.</strong>@endif</p>
<p>Secuencia: {{ $latest->sequence }} · perfil: {{ $latest->profile_version }} · acelerómetro RAW: {{ $latest->ax }}, {{ $latest->ay }}, {{ $latest->az }} · giroscopio RAW: {{ $latest->gx }}, {{ $latest->gy }}, {{ $latest->gz }}.</p>
@endif
<p><a class="button" href="{{ route($prefix.'.support.create',['device_id'=>$device->id]) }}">Registrar incidencia</a>
<a class="button quiet" href="{{ route($prefix.'.devices.diagnostics',['device'=>$device->id]) }}">Actualizar señal</a></p>
<h3>Eventos detectados por el dispositivo</h3>
@forelse($events as $event)<p>{{ ['1'=>'Aceleración sobre límite','2'=>'Frenada sobre límite','3'=>'Giro sobre límite'][$event->type] }} · {{ \App\Support\DashboardDisplay::date(\Carbon\CarbonImmutable::parse($event->received_at,'UTC')) }}</p>@empty<p>Sin eventos recibidos de este dispositivo.</p>@endforelse
<p>Los límites son experimentales y dependen del montaje y de las maniobras aprendidas. Un evento del sensor no se registra automáticamente como una incidencia de soporte.</p>
</section>
