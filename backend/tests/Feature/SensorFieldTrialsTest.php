<?php

namespace Tests\Feature;

use App\Models\Device;
use App\Models\User;
use App\Services\MqttDiagnosticsIngest;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Str;
use Tests\TestCase;

class SensorFieldTrialsTest extends TestCase
{
    use RefreshDatabase;

    private function fixture(): array
    {
        $driver = User::factory()->create(['role' => 'driver']);
        $device = Device::forceCreate(['company_id' => $driver->company_id, 'user_id' => $driver->id, 'client_device_id' => (string) Str::uuid(), 'name' => 'TEST AVIGEM']);
        $this->artisan('mqtt:bind-device', ['device-id' => $device->id, 'device-code' => 'TEST-ONE'])->assertSuccessful();
        $data = ['protocol' => 2, 'deviceCode' => 'TEST-ONE', 'sessionId' => '566b07e4-78ca-4f0b-a15c-7ed5705ca2a0', 'sequence' => 1,
            'axRaw' => 100, 'ayRaw' => 200, 'azRaw' => 16384, 'gxRaw' => 0, 'gyRaw' => 0, 'gzRaw' => 0, 'profileVersion' => 1, 'event' => 2, 'range' => 2, 'flags' => 3, 'capturedAt' => now()->getTimestampMs()];

        return [$driver, $device, $data];
    }

    public function test_same_sample_and_event_received_by_both_paths_are_stored_once(): void
    {
        [$driver,$device,$data] = $this->fixture();
        $ingest = app(MqttDiagnosticsIngest::class);
        $this->assertTrue($ingest->store('avigem/devices/TEST-ONE/imu', json_encode($data)));
        $this->assertTrue($ingest->store('avigem/devices/TEST-ONE/imu', json_encode($data), false, 'BLE_API', $driver->id));
        $this->assertDatabaseCount('diagnostic_samples', 1);
        $this->assertDatabaseCount('diagnostic_sensor_events', 1);
        $row = DB::table('diagnostic_samples')->first();
        $this->assertNotNull($row->mqtt_received_at);
        $this->assertNotNull($row->ble_received_at);
        $this->assertFalse($ingest->store('avigem/devices/TEST-ONE/imu', json_encode(array_replace($data, ['axRaw' => 999]))));
        $this->assertDatabaseHas('diagnostic_samples', ['ax' => 100]);
    }

    public function test_company_is_visual_only_and_global_admin_has_calibration_controls(): void
    {
        [$driver,$device,$data] = $this->fixture();
        app(MqttDiagnosticsIngest::class)->store('avigem/devices/TEST-ONE/imu', json_encode($data));
        $fleet = User::factory()->create(['role' => 'fleet_admin', 'company_id' => $device->company_id]);
        $this->actingAs($fleet, 'web')->get('/dashboard/devices/diagnostics')->assertOk()->assertSee('Fuera del rango aprendido')->assertSee('Registrar incidencia')->assertDontSee('1. Calibrar reposo');
        foreach (['start', 'import'] as $action) {
            $this->post('/dashboard/devices/diagnostics/'.$action, [])->assertForbidden();
        }
        $global = User::factory()->create(['role' => 'platform_admin', 'company_id' => null]);
        $this->actingAs($global, 'web')->get('/admin/devices/diagnostics')->assertOk()->assertSee('1. Calibrar reposo')->assertSee('Calibración del ESP32');
        $this->post('/admin/devices/diagnostics/control', ['device' => $device->id, 'operation' => 'rest'])->assertRedirect();
        $this->assertDatabaseHas('diagnostic_device_commands', ['device_id' => $device->id, 'operation' => 'rest', 'status' => 'QUEUED']);
    }

    public function test_acknowledgement_requires_mqtt_from_the_bound_device(): void
    {
        [$driver,$device,$data] = $this->fixture();
        $id = (string) Str::uuid();
        DB::table('diagnostic_device_commands')->insert(['id' => $id, 'device_id' => $device->id, 'user_id' => $driver->id, 'operation' => 'rest', 'status' => 'PUBLISHED', 'created_at' => now()]);
        $data['ack'] = $id;
        $ingest = app(MqttDiagnosticsIngest::class);
        $this->assertTrue($ingest->store('avigem/devices/TEST-ONE/imu', json_encode($data), false, 'BLE_API', $driver->id));
        $this->assertDatabaseHas('diagnostic_device_commands', ['id' => $id, 'status' => 'PUBLISHED']);
        $this->assertTrue($ingest->store('avigem/devices/TEST-ONE/imu', json_encode($data)));
        $this->assertDatabaseHas('diagnostic_device_commands', ['id' => $id, 'status' => 'ACKNOWLEDGED']);
    }

    public function test_relay_cannot_claim_another_company_device_and_old_capture_is_not_fresh(): void
    {
        [$driver,$device,$data] = $this->fixture();
        $foreign = User::factory()->create(['role' => 'driver']);
        $ingest = app(MqttDiagnosticsIngest::class);
        $this->assertFalse($ingest->store('avigem/devices/TEST-ONE/imu', json_encode($data), false, 'BLE_API', $foreign->id));
        $data['capturedAt'] = now()->subHour()->getTimestampMs();
        $this->assertTrue($ingest->store('avigem/devices/TEST-ONE/imu', json_encode($data), false, 'BLE_API', $driver->id));
        $fleet = User::factory()->create(['role' => 'fleet_admin', 'company_id' => $device->company_id]);
        $this->actingAs($fleet,'web')->get('/dashboard/devices/diagnostics')->assertOk()->assertSee('Sin señal reciente');
    }
}
