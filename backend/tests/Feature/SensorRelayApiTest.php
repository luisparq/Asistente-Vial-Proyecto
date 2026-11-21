<?php

namespace Tests\Feature;

use App\Models\Device;
use App\Models\Driver;
use App\Models\User;
use App\Services\MqttDiagnosticsIngest;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Auth;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Str;
use Tests\TestCase;

/** Synthetic fixtures: transport, authorization and idempotency only. */
class SensorRelayApiTest extends TestCase
{
    use RefreshDatabase;

    private function fixture(): array
    {
        $driver = User::factory()->create(['role' => 'driver']);
        Driver::forceCreate(['company_id' => $driver->company_id, 'user_id' => $driver->id, 'full_name' => 'TEST ONLY driver', 'status' => 'ACTIVE']);
        $device = Device::forceCreate(['company_id' => $driver->company_id, 'user_id' => $driver->id, 'client_device_id' => (string) Str::uuid(), 'name' => 'TEST ONLY AVIGEM']);
        $this->artisan('mqtt:bind-device', ['device-id' => $device->id, 'device-code' => 'TEST-RELAY'])->assertSuccessful();
        $sample = ['protocol' => 2, 'deviceCode' => 'TEST-RELAY', 'sessionId' => '566b07e4-78ca-4f0b-a15c-7ed5705ca2a0', 'sequence' => 1,
            'axRaw' => 100, 'ayRaw' => 200, 'azRaw' => 16384, 'gxRaw' => -130, 'gyRaw' => 0, 'gzRaw' => 0, 'profileVersion' => 1, 'event' => 2, 'range' => 2, 'flags' => 3, 'capturedAt' => now()->getTimestampMs()];

        return [$driver, $device, $sample];
    }

    private function headers(User $user): array
    {
        Auth::forgetGuards();

        return ['Authorization' => 'Bearer '.$user->createToken('TEST ONLY', $user->capabilities(), now()->addHour())->plainTextToken];
    }

    public function test_real_token_batch_over_16kb_is_acknowledged_with_complete_sample_identity(): void
    {
        [$driver,$device,$sample] = $this->fixture();
        $batch = [];
        for ($i = 0; $i < 100; $i++) {
            $batch[] = array_replace($sample, ['sequence' => $i, 'event' => $i === 20 ? 2 : 0]);
        }
        $this->assertGreaterThan(16384, strlen(json_encode(['samples' => $batch])));
        $reply = $this->postJson('/api/v1/sensor-samples', ['samples' => $batch], $this->headers($driver))->assertOk()->assertJsonCount(100, 'accepted');
        $reply->assertJsonPath('accepted.0.deviceCode', 'TEST-RELAY')->assertJsonPath('accepted.0.sessionId', $sample['sessionId'])->assertJsonPath('accepted.99.sequence', 99);
        $this->assertDatabaseCount('diagnostic_samples', 100);
        $this->assertDatabaseCount('diagnostic_sensor_events', 1);
    }

    public function test_ble_first_then_mqtt_enriches_the_same_sample_without_trusting_phone_control_metadata(): void
    {
        [$driver,$device,$sample] = $this->fixture();
        $sample['calibrationStatus'] = 'PROFILE_APPLIED';
        $sample['accelerationLimit'] = 0.2;
        $this->postJson('/api/v1/sensor-samples', ['samples' => [$sample]], $this->headers($driver))->assertOk();
        $state = DB::table('diagnostic_device_state')->first();
        $this->assertNull($state->mqtt_payload);
        $this->assertArrayNotHasKey('calibrationStatus', json_decode($state->payload, true));
        $this->assertTrue(app(MqttDiagnosticsIngest::class)->store('avigem/devices/TEST-RELAY/imu', json_encode($sample)));
        $state = DB::table('diagnostic_device_state')->first();
        $this->assertSame('PROFILE_APPLIED', json_decode($state->mqtt_payload, true)['calibrationStatus']);
        $this->assertDatabaseCount('diagnostic_samples', 1);
        $this->assertDatabaseCount('diagnostic_sensor_events', 1);
    }

    public function test_accepts_queued_android_samples_with_the_default_protocol_omitted(): void
    {
        [$driver, , $sample] = $this->fixture();
        unset($sample['protocol']);

        $this->postJson('/api/v1/sensor-samples', ['samples' => [$sample]], $this->headers($driver))
            ->assertOk()->assertJsonPath('accepted.0.sequence', $sample['sequence']);
        $this->assertDatabaseCount('diagnostic_samples', 1);
    }

    public function test_unauthorized_foreign_and_inactive_driver_requests_are_denied(): void
    {
        [$driver,$device,$sample] = $this->fixture();
        $this->postJson('/api/v1/sensor-samples', ['samples' => [$sample]])->assertUnauthorized();
        $other = User::factory()->create(['role' => 'driver']);
        Driver::forceCreate(['company_id' => $other->company_id, 'user_id' => $other->id, 'full_name' => 'TEST ONLY foreign', 'status' => 'ACTIVE']);
        $this->postJson('/api/v1/sensor-samples', ['samples' => [$sample]], $this->headers($other))->assertConflict();
        $driver->driver()->update(['status' => 'SUSPENDED']);
        $this->postJson('/api/v1/sensor-samples', ['samples' => [$sample]], $this->headers($driver))->assertForbidden();
        $this->assertDatabaseCount('diagnostic_samples', 0);
    }

    public function test_invalid_batch_rolls_back_and_conflict_does_not_overwrite_raw(): void
    {
        [$driver,$device,$sample] = $this->fixture();
        $headers = $this->headers($driver);
        $bad = array_replace($sample, ['sequence' => 2, 'flags' => 255]);
        $this->postJson('/api/v1/sensor-samples', ['samples' => [$sample, $bad]], $headers)->assertConflict();
        $this->assertDatabaseCount('diagnostic_samples', 0);
        $this->postJson('/api/v1/sensor-samples', ['samples' => [$sample]], $headers)->assertOk();
        $this->postJson('/api/v1/sensor-samples', ['samples' => [array_replace($sample, ['axRaw' => 999])]], $headers)->assertConflict();
        $this->assertDatabaseHas('diagnostic_samples', ['ax' => 100]);
    }

    public function test_malformed_metadata_and_timestamp_never_cause_a_server_error(): void
    {
        [$driver,$device,$sample] = $this->fixture();
        $headers = $this->headers($driver);
        foreach ([['capturedAt' => PHP_INT_MAX], ['capturedAt' => -1], ['protocol' => 3], ['calibrationStatus' => ['BAD']], ['accelerationLimit' => ['BAD']], ['ack' => 'not-a-command']] as $invalid) {
            $this->postJson('/api/v1/sensor-samples', ['samples' => [array_replace($sample,$invalid)]], $headers)->assertConflict();
        }
        $this->assertDatabaseCount('diagnostic_samples',0);
    }
}
