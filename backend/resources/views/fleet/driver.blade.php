@extends('dashboard.layout')
@section('title',$driver->full_name)
@section('data-notice', '')
@section('content')
<div class="panel fleet-profile-summary">
    <div class="fleet-record-identity">
        @include('fleet.driver-avatar')
        <div><h2>Conducción · últimos 30 días</h2><p>{{ $driver->scoring_trips_count }} recorridos registrados · {{ $driver->scoring_events_count }} eventos {{ $driver->score_origin === 'SIMULATED' ? 'simulados' : 'de sensor' }}</p></div>
    </div>
    <div class="fleet-record-score">
        <strong>{{ $driver->score() === null ? 'Sin puntaje' : $driver->score().'/100' }}</strong>
        @if($driver->score() !== null)
            <small>{{ $driver->score_origin === 'SIMULATED' ? 'Puntaje de prueba' : 'Puntaje provisional' }}</small>
        @endif
    </div>
</div>
@include('fleet.driver-history')
<section class="panel"><h2>Perfil y cuenta</h2><p>{{ $driver->user->email }}</p>
@if($driver->phone)<p>Celular: {{ $driver->phone }}</p>@endif
@if($driver->address)<p>Dirección: {{ $driver->address }}</p>@endif
<p>Estado del conductor: {{ $driver->status === 'ACTIVE' ? 'Activo' : 'Inactivo' }}</p><a class="button quiet" href="{{ route('dashboard.drivers.edit',$driver->id) }}">Editar perfil y documentos</a>
<h3>Acceso a la aplicación</h3>
@php($access = $driver->user->access_status)
<p>Acceso a la app: {{ ['PENDING'=>'Pendiente de activación','ACTIVE'=>'Activo','DISABLED'=>'Deshabilitado'][$access] ?? $access }}</p>
@if($access === 'PENDING')<p>El conductor todavía no ha activado su cuenta en la aplicación móvil.</p>
@elseif($access === 'ACTIVE')<p>El conductor tiene acceso a la aplicación móvil.</p>
@elseif($access === 'DISABLED')<p>El conductor no puede iniciar sesión en la aplicación.</p>@endif
<form method="post" action="{{ route('dashboard.drivers.access',$driver->id) }}" class="actions">@csrf
@if($access === 'PENDING' && $driver->status === 'ACTIVE')<button name="action" value="invite" class="button">Generar código de activación</button>
@elseif($access === 'ACTIVE')<button name="action" value="disable" class="button quiet">Deshabilitar acceso</button>
@elseif($access === 'DISABLED' && $driver->status === 'ACTIVE')<button name="action" value="enable" class="button quiet">Permitir nueva activación</button>@endif
</form>
<h3>Contacto de emergencia</h3>@if($driver->emergencyContact)<p>{{ $driver->emergencyContact->name }} · {{ $driver->emergencyContact->relationship }} · {{ $driver->emergencyContact->phone }}</p>@else<p>Sin contacto de emergencia.</p>@endif</section>
<section class="panel"><h2>Vehículo actual</h2><p>{{ $driver->currentAssignment?->vehicle->plate ?? 'Sin vehículo asignado' }}</p>
<form method="post" action="{{ route('dashboard.drivers.assign',$driver->id) }}" class="fleet-form">@csrf<label>Asignar / reasignar<select name="vehicle_id"><option value="">Finalizar asignación / sin vehículo</option>@foreach($vehicles as $vehicle)<option value="{{ $vehicle->id }}" @selected($driver->currentAssignment?->vehicle_id === $vehicle->id)>{{ $vehicle->plate }}</option>@endforeach</select></label><button class="button">Guardar asignación</button></form>
<h3>Historial de asignaciones</h3>@forelse($assignments as $assignment)<p>{{ $assignment->vehicle->plate }} · {{ \App\Support\DashboardDisplay::date($assignment->started_at) }} → {{ $assignment->ended_at ? \App\Support\DashboardDisplay::date($assignment->ended_at) : 'Actual' }}</p>@empty<p>Sin asignaciones.</p>@endforelse
@include('dashboard.partials.pagination',['rows'=>$assignments])</section>
<section class="panel"><h2>Documentación privada</h2><p>Revisión administrativa; no constituye validación legal. El vencimiento se calcula independientemente.</p>
@forelse($driver->documents as $document)<article><h3>{{ $document->type->name }}</h3><p>Referencia: {{ $document->reference ?? 'No indicada' }} · {{ $document->review_status }} · Vence: {{ $document->expires_on?->format('d/m/Y') ?? 'No indicado' }} {{ $document->expired()?'(VENCIDO)':'' }}</p><a href="{{ route('dashboard.documents.download',$document->id) }}">Descargar archivo privado</a><form method="post" action="{{ route('dashboard.documents.review',$document->id) }}" class="actions">@csrf<label>Revisión<select name="review_status">@foreach(['PENDING','VERIFIED','REJECTED'] as $state)<option @selected($document->review_status===$state)>{{ $state }}</option>@endforeach</select></label><button class="button quiet">Guardar revisión</button></form></article>@empty<p>Sin documentos.</p>@endforelse
<details><summary>Adjuntar documento</summary><form method="post" enctype="multipart/form-data" action="{{ route('dashboard.drivers.documents',$driver->id) }}" class="fleet-form">@csrf<label>Tipo<select name="document_type_id">@foreach($types as $type)<option value="{{ $type->id }}">{{ $type->name }}</option>@endforeach</select></label><label>Referencia (opcional)<input name="reference" maxlength="255"></label><label>Emisión (opcional)<input name="issued_on" type="date"></label><label>Vencimiento (opcional)<input name="expires_on" type="date"></label><label>Archivo PDF, JPEG o PNG · máximo 5 MiB<input type="file" name="document" accept=".pdf,.jpg,.jpeg,.png" required></label><button class="button">Guardar documento privado</button></form></details></section>
@endsection
