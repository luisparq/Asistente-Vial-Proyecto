<?php

namespace Tests\Feature;

use App\Models\Device;
use App\Models\User;
use App\Services\MqttDiagnosticsIngest;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Str;
use Tests\TestCase;

class MqttDiagnosticsTest extends TestCase
{
    use RefreshDatabase;

    private const PAGE = '/dashboard/devices/diagnostics';

    // Synthetic telemetry fixtures. They are never sent to the application database.
    private const SESSION = '566b07e4-78ca-4f0b-a15c-7ed5705ca2a0';

    public function test_replayed_sequence_keeps_original_values_and_receipt_time(): void
    {
        $this->freezeTime();
        $device = $this->bind('TEST-ESP-A');
        $this->assertTrue($this->receive('TEST-ESP-A'));
        $original = DB::table('diagnostic_samples')->first();
        $this->travel(40)->seconds();
        $this->assertFalse($this->receive('TEST-ESP-A', ['axRaw' => 999]));
        $this->assertTrue($this->receive('TEST-ESP-A', ['sequence' => 1]));
        $this->assertDatabaseCount('diagnostic_sessions', 1);
        $this->assertDatabaseCount('diagnostic_samples', 2);
        $this->assertDatabaseHas('diagnostic_sessions', [
            'company_id' => $device->company_id, 'device_id' => $device->id,
            'source_session_id' => self::SESSION, 'finished_at' => null,
        ]);
        $this->assertDatabaseHas('diagnostic_samples', (array) $original);
    }

    public function test_invalid_payloads_unmatched_topics_and_retained_messages_are_discarded(): void
    {
        $this->bind('TEST-ESP-A');
        $ingest = app(MqttDiagnosticsIngest::class);
        $this->assertFalse($ingest->store('avigem/devices/TEST-ESP-A/imu', '{'));
        $this->assertFalse($ingest->store('unrelated/topic', json_encode($this->payload('TEST-ESP-A'))));
        $this->assertFalse($ingest->store('avigem/devices/TEST-ESP-A/imu', json_encode($this->payload('TEST-ESP-A')), true));
        foreach ([
            ['sessionId' => 'invalid'], ['sequence' => -1], ['sequence' => '1'],
            ['deviceCode' => 'TEST-ESP-B'], ['axRaw' => 32768], ['ayRaw' => null],
            ['azRaw' => -32769], ['gyRaw' => 1.5], ['gzRaw' => true],
        ] as $invalid) {
            $this->assertFalse($this->receive('TEST-ESP-A', $invalid));
        }
        $this->assertDatabaseCount('diagnostic_sessions', 0);
        $this->assertDatabaseCount('diagnostic_samples', 0);
    }

    public function test_unknown_device_codes_are_discarded_and_bindings_cannot_be_reassigned(): void
    {
        $device = $this->bind('TEST-ESP-A');
        $this->assertFalse($this->receive('UNKNOWN-TEST-ESP'));
        $this->assertFalse($this->receive('test-esp-a'));
        $this->assertDatabaseCount('diagnostic_sessions', 0);

        $other = $this->device('TEST-ESP-B');
        $this->artisan('mqtt:bind-device', ['device-id' => $other->id, 'device-code' => 'TEST-ESP-A'])->assertFailed();
        $this->artisan('mqtt:bind-device', ['device-id' => $device->id, 'device-code' => 'TEST-ESP-A'])->assertSuccessful();
        $this->assertDatabaseCount('diagnostic_device_codes', 1);
    }

    public function test_tenants_and_device_sessions_are_isolated_and_chart_has_a_30_second_window(): void
    {
        $this->freezeTime();
        $own = $this->bind('TEST-ESP-A');
        $other = $this->bind('TEST-ESP-B');
        $this->assertTrue($this->receive('TEST-ESP-A'));
        $this->assertTrue($this->receive('TEST-ESP-B', ['axRaw' => 12345]));
        $this->travel(15)->seconds();
        $this->assertTrue($this->receive('TEST-ESP-A', ['sequence' => 1, 'axRaw' => 200]));
        $this->travel(30)->seconds();
        $this->assertTrue($this->receive('TEST-ESP-A', ['sequence' => 2, 'axRaw' => 300]));

        $this->assertDatabaseCount('diagnostic_sessions', 2);
        $foreignSession = DB::table('diagnostic_sessions')->where('device_id', $other->id)->value('id');
        $ownSession = DB::table('diagnostic_sessions')->where('device_id', $own->id)->value('id');
        $admin = User::factory()->create(['company_id' => $own->company_id, 'role' => 'fleet_admin']);
        $this->actingAs($admin, 'web')->get('/dashboard/devices')->assertOk()->assertSee(self::PAGE);
        $this->get(self::PAGE.'?device='.$own->id.'&session='.$ownSession)
            ->assertOk()->assertSee('Telemetría experimental; no valida el sensor')->assertSee('<polyline', false)
            ->assertViewHas('samples', fn ($samples) => $samples->pluck('ax')->all() === [200, 300])
            ->assertDontSee('TEST-ESP-B')->assertDontSee('12345');
        $this->get(self::PAGE.'?device='.$other->id)->assertNotFound();
        $this->get(self::PAGE.'?device='.$own->id.'&session='.$foreignSession)->assertNotFound();
    }

    public function test_only_active_fleet_admins_can_view_diagnostics(): void
    {
        $this->get(self::PAGE)->assertRedirect('/dashboard/login');

        foreach ([
            ['role' => 'member'],
            ['role' => 'platform_admin', 'company_id' => null],
            ['role' => 'fleet_admin', 'access_status' => 'DISABLED'],
        ] as $attributes) {
            $this->actingAs(User::factory()->create($attributes), 'web')->get(self::PAGE)->assertForbidden();
        }
        $this->actingAs(User::factory()->create(['role' => 'fleet_admin']), 'web')
            ->get(self::PAGE)->assertOk()->assertSee('todavía no tiene dispositivos');
    }

    private function device(string $code): Device
    {
        $owner = User::factory()->create(['role' => 'driver']);

        return Device::forceCreate([
            'company_id' => $owner->company_id, 'user_id' => $owner->id,
            'client_device_id' => (string) Str::uuid(), 'name' => $code,
        ]);
    }

    private function bind(string $code): Device
    {
        $device = $this->device($code);
        $this->artisan('mqtt:bind-device', ['device-id' => $device->id, 'device-code' => $code])->assertSuccessful();

        return $device;
    }

    private function payload(string $code): array
    {
        return ['deviceCode' => $code, 'sessionId' => self::SESSION, 'sequence' => 0,
            'axRaw' => -32768, 'ayRaw' => 32767, 'azRaw' => 30, 'gxRaw' => 40, 'gyRaw' => 50, 'gzRaw' => 60];
    }

    private function receive(string $code, array $changes = []): bool
    {
        return app(MqttDiagnosticsIngest::class)->store(
            "avigem/devices/{$code}/imu", json_encode(array_replace($this->payload($code), $changes), JSON_THROW_ON_ERROR),
        );
    }
}
