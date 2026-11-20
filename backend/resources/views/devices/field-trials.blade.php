<section class="panel">
    <h2>Prueba del sensor · {{ $device->name }}</h2>
    @if($global)<p>Empresa: <strong>{{ $device->company_name }}</strong></p>@endif
    <p>Para el carro: captura en Android sin Internet e importa el CSV al regresar. Desde esta web también puedes delimitar una prueba cuando el dispositivo está enviando MQTT al servidor. Los botones web no controlan el Bluetooth del teléfono ni encienden el sensor.</p>
    <h3>1. Preparar montaje</h3>
    <p>Fija el módulo al vehículo y anota hacia dónde apuntan X, Y y Z. El acompañante usa los controles; las etiquetas son anotaciones manuales.</p>
    @if($active)
        <h3>{{ $active->phase === 'REST' ? '2. Midiendo reposo · 30 segundos' : '3. Grabando recorrido' }}</h3>
        <p>Prueba #{{ $active->id }} · {{ $active->mounting }} · iniciada {{ \App\Support\DashboardDisplay::date(\Carbon\CarbonImmutable::parse($active->started_at, 'UTC')) }} (Lima).</p>
        @if($active->phase === 'REST')
            <p>Mantén el carro detenido, motor apagado y sensor inmóvil. Espera 30 segundos antes de finalizar. Una medición más corta se conserva, pero no habilita la grabación.</p>
        @else
            <form class="filters" method="post" action="{{ route($prefix.'.devices.diagnostics.label', $active->id) }}">@csrf
                <label>Maniobra desde ahora<select name="label">@foreach(\App\Services\SensorFieldTrials::LABELS as $key=>$label)<option value="{{ $key }}">{{ $label }}</option>@endforeach</select></label>
                <button class="button">Marcar maniobra</button>
            </form>
        @endif
        <form method="post" action="{{ route($prefix.'.devices.diagnostics.stop', $active->id) }}">@csrf
            <button class="button primary">{{ $active->phase === 'REST' ? 'Finalizar reposo y guardar' : 'Detener y guardar' }}</button>
        </form>
        <p><a class="button quiet" href="{{ route($prefix.'.devices.diagnostics', ['device'=>$device->id,'session'=>$active->session_id,'trial'=>$active->id]) }}">Actualizar recepción</a></p>
        <p>El servidor conserva la telemetría aunque cierres esta página. Finaliza la prueba al terminar. Ventana máxima de recorrido: 2 horas; los cortes de recepción se señalan en el resumen.</p>
    @elseif($session)
        <form class="filters" method="post" action="{{ route($prefix.'.devices.diagnostics.start') }}">@csrf
            <input type="hidden" name="device" value="{{ $device->id }}"><input type="hidden" name="session" value="{{ $session->id }}"><input type="hidden" name="phase" value="REST">
            <label>Montaje y orientación<input name="mounting" required maxlength="500" value="{{ old('mounting', $references->first()?->mounting) }}" placeholder="Ej.: consola fija; X adelante, Y izquierda, Z arriba"></label>
            <button class="button primary">Medir reposo · 30 s</button>
        </form>
        <h3>3. Grabar recorrido</h3>
        @if($references->isNotEmpty())
            <form class="filters" method="post" action="{{ route($prefix.'.devices.diagnostics.start') }}">@csrf
                <input type="hidden" name="device" value="{{ $device->id }}"><input type="hidden" name="session" value="{{ $session->id }}"><input type="hidden" name="phase" value="RECORDING">
                <label>Referencia de reposo<select name="reference">@foreach($references as $reference)<option value="{{ $reference->id }}">#{{ $reference->id }} · {{ $reference->mounting }}</option>@endforeach</select></label>
                <label>Confirma el mismo montaje<input name="mounting" value="{{ $references->first()->mounting }}" required maxlength="500"></label>
                <button class="button primary">Iniciar grabación</button>
            </form>
        @else<p>Mide primero un reposo estable en esta sesión MQTT para habilitar el recorrido web.</p>@endif
    @else
        <p>Sin sesión MQTT. Puedes importar una prueba de Android debajo. Para iniciar una prueba web, primero debe llegar telemetría MQTT de este dispositivo.</p>
    @endif
</section>
<section class="panel">
    <h2>Importar prueba del teléfono</h2>
    <p>Selecciona el CSV de reposo o recorrido exportado por «Prueba del sensor». Se asociará al dispositivo {{ $device->name }} de {{ $device->company_name }}; comprueba que sea el correcto. Cada importación conserva su origen Android, montaje, seis ejes, etiquetas y GPS. Máximo 10 MB por archivo.</p>
    <form class="filters" method="post" enctype="multipart/form-data" action="{{ route($prefix.'.devices.diagnostics.import') }}">@csrf
        <input type="hidden" name="device" value="{{ $device->id }}">
        <label>Archivo CSV<input type="file" name="csv" accept=".csv,text/csv" required></label><button class="button primary">Importar CSV</button>
    </form>
</section>
<section class="panel table-wrap">
    <h2>4. Pruebas guardadas y exportación</h2>
    <p>Reposo listo = al menos 100 muestras durante aproximadamente 30 s, sin intervalos mayores de 750 ms, DE ≤0.02 g / ≤1 °/s. Es una referencia experimental; no acredita precisión física. Si cambias montaje o reinicias el sensor, repite el reposo.</p>
    <table><thead><tr><th>Prueba</th><th>Origen / fase</th><th>Estado</th><th>Montaje</th><th>Acciones</th></tr></thead><tbody>
    @forelse($trials as $item)
        <tr><td>#{{ $item->id }}<br>{{ \App\Support\DashboardDisplay::date(\Carbon\CarbonImmutable::parse($item->started_at, 'UTC')) }}</td>
            <td>{{ $item->source === 'ANDROID_CSV' ? 'CSV Android (importado)' : 'MQTT servidor' }}<br>{{ $item->phase === 'REST' ? 'Reposo' : 'Recorrido' }}</td>
            <td>{{ ['ACTIVE'=>'En curso','READY'=>'Referencia lista','REJECTED'=>'Repetir reposo','COMPLETED'=>'Guardada','INCOMPLETE'=>'Archivo incompleto'][$item->status] ?? $item->status }}</td><td>{{ $item->mounting }}</td>
            <td><a class="button quiet" href="{{ route($prefix.'.devices.diagnostics', array_filter(['device'=>$device->id,'session'=>$session?->id,'trial'=>$item->id])) }}">Ver valores</a>
            @if($item->status !== 'ACTIVE')<a class="button" href="{{ route($prefix.'.devices.diagnostics.export', $item->id) }}">Exportar CSV</a>@endif</td></tr>
    @empty<tr><td colspan="5">Todavía no hay pruebas guardadas para este dispositivo.</td></tr>@endforelse
    </tbody></table>
    @if($trials instanceof \Illuminate\Contracts\Pagination\Paginator)@include('dashboard.partials.pagination',['rows'=>$trials])@endif
</section>
@if($trialSummary)
<section class="panel table-wrap">
    <h2>Valores de la prueba #{{ $trial->id }}</h2>
    <p>{{ $trialSummary['samples'] }} muestras · {{ number_format($trialSummary['duration_ms']/1000,1) }} s entre primera y última muestra · {{ number_format($trialSummary['hz'],2) }} Hz · {{ $trialSummary['gaps'] }} intervalos mayores de 750 ms.</p>
    <table><thead><tr><th>Eje</th><th>Media</th><th>Desviación estándar</th><th>Mínimo</th><th>Máximo</th><th>Unidad nominal</th></tr></thead><tbody>
        @foreach($trialSummary['axes'] as $axis)<tr><td>{{ $axis['axis'] }}</td>@foreach(['mean','sd','min','max'] as $stat)<td>{{ $axis[$stat] === null ? '—' : number_format($axis[$stat],5) }}</td>@endforeach<td>{{ $axis['unit'] }}</td></tr>@endforeach
    </tbody></table>
    <p>Etiquetas manuales (muestras): @foreach($trialSummary['labels'] as $label=>$count){{ \App\Services\SensorFieldTrials::LABELS[$label] ?? 'Reposo' }}: {{ $count }}. @endforeach</p>
    <p>Se conservan los valores RAW originales. La aceleración incluye gravedad; restar el reposo no compensa cambios de inclinación. No se generan eventos de conducción ni se activan alertas desde esta herramienta.</p>
    <p>{{ $trial->source === 'MQTT' ? 'Tiempos de recepción del servidor. Este canal actual no trae GPS; no se inventan coordenadas.' : 'Tiempos de recepción Android conservados del archivo. CSV importado por un usuario: no es una verificación independiente del hardware.' }}</p>
</section>
@endif
