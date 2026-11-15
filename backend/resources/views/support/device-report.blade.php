<section class="panel detail">
    <h2>Últimos reportes conocidos del dispositivo</h2>
    <p>{{ $device->name }} · {{ $device->client_device_id }}</p>
    <dl class="details">
        <div><dt>Empresa</dt><dd>{{ $device->company->name }}</dd></div>
        <div><dt>Comunicación Android–Laravel</dt><dd>{{ $device->communicationState() }}</dd></div>
        <div><dt>Reporte observado · Lima</dt><dd>{{ \App\Support\DashboardDisplay::date($device->observed_at) }}</dd></div>
        <div><dt>Reporte recibido · Lima</dt><dd>{{ \App\Support\DashboardDisplay::date($device->received_at) }}</dd></div>
        <div><dt>Última muestra BLE · Lima</dt><dd>{{ \App\Support\DashboardDisplay::date($device->last_contact_at) }}</dd></div>
        @foreach(['ble_state' => 'BLE', 'imu_state' => 'IMU', 'gps_state' => 'GPS'] as $field => $label)
            <div><dt>{{ $label }}</dt><dd>{{ $device->$field ?? 'Sin información' }}</dd></div>
        @endforeach
        <div><dt>Error reportado</dt><dd>{{ $device->technical_error ?? 'Sin error reportado' }}</dd></div>
    </dl>
    <p class="muted">Estado según el último reporte recibido. La conexión BLE actual y el encendido del vehículo requieren verificación en sitio.</p>
</section>
