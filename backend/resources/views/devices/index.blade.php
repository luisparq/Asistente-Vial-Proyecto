@extends($global ? 'platform.layout' : 'dashboard.layout')
@section('title','Estado de dispositivos')
@section('description','Última información técnica reportada desde Android')
@section('content')
@include('devices.tabs', ['diagnosticsTab' => false])
<p>Android envía como máximo un reporte por minuto mientras la aplicación está abierta. Comunicación reciente: reporte de los últimos 3 minutos. No garantiza que el ESP32 siga conectado. Un envío offline antiguo no se considera reciente.</p>
<section class="panel device-report-table"><table role="table" aria-label="Reportes de dispositivos"><thead role="rowgroup"><tr role="row">@if($global)<th scope="col" role="columnheader">Empresa</th>@endif<th scope="col" role="columnheader">Dispositivo / identificador local</th><th scope="col" role="columnheader">Conductor / vehículo actual</th><th scope="col" role="columnheader">Comunicación Android</th><th scope="col" role="columnheader">BLE / IMU / GPS</th><th scope="col" role="columnheader">Último reporte / contacto BLE</th><th scope="col" role="columnheader">Error técnico</th></tr></thead><tbody role="rowgroup">
@forelse($devices as $device)<tr role="row">
@if($global)<td role="cell" data-label="Empresa">{{ $device->company->name }}</td>@endif
<td role="cell" data-label="Dispositivo"><a href="{{ route(($global ? 'platform' : 'dashboard').'.devices.show',$device->id) }}">{{ $device->name }}</a><details><summary>Identificador local</summary><code class="uuid">{{ $device->client_device_id }}</code></details></td>
<td role="cell" data-label="Conductor / vehículo">{{ $device->user->name }}<small>{{ $device->user->driver?->currentAssignment?->vehicle?->plate ?? 'Sin vehículo asignado actualmente' }}</small></td>
<td role="cell" data-label="Comunicación Android"><x-comm-state :state="$device->communicationState()"/></td>
<td role="cell" data-label="Estado del sensor">BLE: {{ $device->ble_state ?? 'Sin información' }}<br>IMU: {{ $device->imu_state ?? 'Sin información' }}<br>GPS: {{ $device->gps_state ?? 'Sin información' }}</td>
<td role="cell" data-label="Últimos reportes">Reporte observado: {{ \App\Support\DashboardDisplay::date($device->observed_at) }}<br>Recibido por servidor: {{ \App\Support\DashboardDisplay::date($device->received_at) }}<br>Última muestra BLE: {{ \App\Support\DashboardDisplay::date($device->last_contact_at) }}</td>
<td role="cell" data-label="Error técnico">{{ $device->technical_error ?? 'Sin error reportado' }}</td></tr>
@empty<tr role="row"><td role="cell" colspan="{{ $global ? 7 : 6 }}">Sin información: todavía no se han recibido reportes de dispositivos.</td></tr>@endforelse
</tbody></table>@include('dashboard.partials.pagination',['rows'=>$devices])</section>
<p>La asignación corresponde al dispositivo recordado por esta cuenta en Android; no confirma la identidad física del sensor ni la propiedad del hardware. El vehículo es la asignación actual del conductor, no una vinculación permanente del dispositivo.</p>
@endsection
