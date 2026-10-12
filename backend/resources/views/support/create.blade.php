@extends($global?'platform.layout':'dashboard.layout')
@section('title','Registrar incidencia')
@section('content')
<section class="panel"><form method="post" action="{{ route($base.'.store') }}" class="fleet-form">@csrf
@if($global)<label>Empresa<select name="company_id" required><option value="">Seleccionar empresa</option>@foreach($companies as $company)<option value="{{ $company->id }}" @selected(old('company_id')==$company->id)>{{ $company->name }}</option>@endforeach</select></label>@endif
<label>Asunto<input name="subject" required maxlength="180" value="{{ old('subject') }}"></label><label>Descripción<textarea name="description" required maxlength="5000" rows="6">{{ old('description') }}</textarea></label>
@foreach(['category'=>['Categoría',\App\Models\SupportTicket::CATEGORIES],'priority'=>['Prioridad',\App\Models\SupportTicket::PRIORITIES]] as $key=>[$label,$options])<label>{{ $label }}<select name="{{ $key }}" required>@foreach($options as $value=>$text)<option value="{{ $value }}" @selected(old($key,$key==='priority'?'MEDIUM':'OTHER')===$value)>{{ $text }}</option>@endforeach</select></label>@endforeach
<label>Dispositivo (opcional)<select name="device_id"><option value="">Sin dispositivo asociado</option>@foreach($devices as $device)<option value="{{ $device->id }}" @selected(old('device_id')==$device->id)>{{ $global?$device->company->name.' · ':'' }}{{ $device->name }} · {{ $device->client_device_id }}</option>@endforeach</select></label><p>El dispositivo debe pertenecer a la empresa seleccionada. Describe lo observado sin incluir contraseñas, tokens ni datos privados de conductores.</p><div class="actions"><button class="button">Registrar ticket</button><a href="{{ route($base) }}">Cancelar</a></div></form></section>
@endsection
