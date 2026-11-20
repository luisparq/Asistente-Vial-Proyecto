@extends($global ? 'platform.layout' : 'dashboard.layout')
@section('title','Diagnóstico de dispositivos')
@section('description','Señales del AVIGEM One y estado de sus límites aprendidos.')
@push('head')<meta http-equiv="refresh" content="5">@endpush
@section('content')
@include('devices.tabs',['diagnosticsTab'=>true])
<p class="notice">Telemetría experimental; no valida el sensor</p>
@if($global)
<form class="filters panel" method="get" action="{{ route($prefix.'.devices.diagnostics') }}"><label>Empresa<select name="company"><option value="">Todas las empresas</option>@foreach($companies as $company)<option value="{{ $company->id }}" @selected($companyId===$company->id)>{{ $company->name }}</option>@endforeach</select></label><button class="button">Filtrar</button></form>
@endif
@if($device)
<form class="filters panel" method="get" action="{{ route($prefix.'.devices.diagnostics') }}">
@if($global && $companyId)<input type="hidden" name="company" value="{{ $companyId }}">@endif
<label>Dispositivo<select name="device">@foreach($devices as $option)<option value="{{ $option->id }}" @selected($option->id===$device->id)>@if($global){{ $option->company_name }} · @endif{{ $option->name }} ({{ $option->code ?? 'Sin vínculo MQTT' }})</option>@endforeach</select></label><button class="button">Consultar señal</button></form>
@include('devices.sensor-monitor')
@if($global)@include('devices.sensor-control')@endif
@if($session)
<form class="filters panel" method="get" action="{{ route($prefix.'.devices.diagnostics') }}"><input type="hidden" name="device" value="{{ $device->id }}"><label>Sesión<select name="session">@foreach($sessions as $option)<option value="{{ $option->id }}" @selected($option->id===$session->id)>#{{ $option->id }} · {{ $option->source_session_id }}</option>@endforeach</select></label><button class="button">Consultar sesión</button></form>
@endif
@foreach([['title'=>'Acelerómetro','data'=>$chart,'names'=>['ax','ay','az']],['title'=>'Giroscopio','data'=>$gyroChart,'names'=>['gx','gy','gz']]] as $plot)
<section class="panel chart"><h2>{{ $plot['title'] }} · últimos 30 segundos</h2>
@if($samples->isEmpty())<p>{{ $session ? 'La sesión todavía no tiene muestras.' : 'Este dispositivo todavía no tiene sesiones.' }}</p>
@else
<p>Hasta {{ \App\Support\DashboardDisplay::date($end) }} (Lima). Cuentas RAW. Tiempos de recepción del servidor o captura del celular, según el primer camino recibido.</p>
<svg viewBox="0 0 840 240" width="100%" role="img" aria-label="{{ $plot['title'] }} en cuentas crudas durante 30 segundos"><path d="M50 20V200H810 M50 110H810" fill="none" stroke="currentColor"/>
<text x="50" y="20">±{{ $plot['data']['scale'] }} cuentas</text><text x="50" y="224">−30 s</text><text x="700" y="224">Última muestra</text>
@foreach(['ax'=>'#255cc7','ay'=>'#b34227','az'=>'#19774d'] as $axis=>$color)<polyline fill="none" stroke="{{ $color }}" stroke-width="2" points="{{ implode(' ',array_map(fn($p)=>implode(',',$p),$plot['data']['series'][$axis])) }}"/>@endforeach
</svg><p>Azul: {{ $plot['names'][0] }} · Naranja: {{ $plot['names'][1] }} · Verde: {{ $plot['names'][2] }}. Actualización cada 5 segundos.</p>
@endif</section>
@endforeach
@if($global)<details><summary>Importar archivo anterior del celular</summary><section class="panel">
<p>Revisión de archivos históricos. Esta importación no calibra ni cambia el perfil del ESP32.</p>
<form class="filters" method="post" enctype="multipart/form-data" action="{{ route('platform.devices.diagnostics.import') }}">@csrf<input type="hidden" name="device" value="{{ $device->id }}"><label>CSV anterior del laboratorio<input type="file" name="csv" accept=".csv,text/csv" required></label><button class="button">Importar CSV</button></form>
@foreach($trials as $item)<p>Archivo #{{ $item->id }} · {{ $item->source }} · {{ $item->status }} @if($item->status!=='ACTIVE')<a href="{{ route('platform.devices.diagnostics.export',$item->id) }}">Exportar CSV</a>@endif</p>@endforeach
</section></details>@endif
@else<p>{{ $global ? 'Todavía no hay dispositivos registrados.' : 'Tu empresa todavía no tiene dispositivos asociados a Diagnóstico.' }}</p>@endif
@endsection
