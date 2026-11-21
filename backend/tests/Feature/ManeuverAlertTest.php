<?php

namespace Tests\Feature;

use App\Models\Driver;
use App\Models\DrivingEvent;
use App\Models\EmergencyContact;
use App\Models\ManeuverAlert;
use App\Models\Trip;
use App\Models\User;
use App\Models\Vehicle;
use App\Models\VehicleAssignment;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Auth;
use Illuminate\Support\Str;
use Tests\TestCase;

class ManeuverAlertTest extends TestCase
{
    use RefreshDatabase;

    private function headers(User $user): array
    {
        Auth::forgetGuards();

        return ['Authorization' => 'Bearer '.$user->createToken('test', $user->capabilities())->plainTextToken];
    }

    private function trip(User $user): Trip
    {
        return Trip::create([
            'company_id' => $user->company_id, 'created_by_user_id' => $user->id, 'client_trip_id' => (string) Str::uuid(),
            'started_at' => '2026-11-15T14:00:00.000Z', 'status' => 'ACTIVE', 'client_revision' => 1,
            'first_received_at' => now(), 'last_changed_at' => now(),
        ]);
    }

    private function event(Trip $trip, string $type, string $origin = 'SIMULATED'): DrivingEvent
    {
        return $trip->events()->create([
            'company_id' => $trip->company_id, 'client_event_id' => (string) Str::uuid(), 'type' => $type,
            'origin' => $origin, 'occurred_at' => '2026-11-15T14:01:00.000Z', 'first_received_at' => now(),
        ]);
    }

    public function test_retries_link_the_same_maneuvers_and_reject_changes_or_another_fleet(): void
    {
        $driver = User::factory()->create(['role' => 'driver']);
        $trip = $this->trip($driver);
        $uuid = (string) Str::uuid();
        $url = "/api/v1/trips/{$trip->client_trip_id}/alerts/$uuid";
        $events = [
            $this->event($trip, 'SHARP_TURN')->client_event_id,
            $this->event($trip, 'HARSH_BRAKING')->client_event_id,
            $this->event($trip, 'HARSH_ACCELERATION')->client_event_id,
            $this->event($trip, 'SHARP_TURN')->client_event_id,
        ];
        $data = ['event_ids' => $events, 'occurred_at' => '2026-11-15T14:01:05.000Z', 'test_only' => true,
            'latitude' => 0.0, 'longitude' => -71.456, 'location_captured_at' => '2026-11-15T14:01:04.000Z',
            'location_status' => 'AVAILABLE'];
        $headers = $this->headers($driver);
        $this->putJson($url, $data, $headers)->assertCreated()->assertJsonPath('data.client_alert_id', $uuid);
        $this->putJson($url, array_replace($data, ['event_ids' => array_reverse($events)]), $headers)->assertOk()
            ->assertJsonPath('sync.disposition', 'already_applied');
        $this->putJson($url, array_replace($data, ['location_status' => 'MISSING', 'latitude' => null,
            'longitude' => null, 'location_captured_at' => null]), $headers)->assertConflict();
        $this->putJson($url, array_replace($data, ['event_ids' => [$events[0], $events[0]]]), $headers)->assertUnprocessable();

        $pending = "/api/v1/trips/{$trip->client_trip_id}/alerts/".Str::uuid();
        $this->putJson($pending, array_replace($data, ['event_ids' => [(string) Str::uuid()]]), $headers)->assertStatus(425);
        $this->putJson($pending, array_replace($data, ['test_only' => false]), $headers)->assertUnprocessable();
        $this->putJson($pending, array_replace($data, ['location_captured_at' => '2026-11-15T14:01:06.000Z']), $headers)->assertUnprocessable();
        $this->putJson($url, $data, $this->headers(User::factory()->create()))->assertNotFound();
        $this->assertDatabaseCount('maneuver_alerts', 1);
        $this->assertDatabaseCount('driving_event_maneuver_alert', 4);
    }

    public function test_fleet_reuses_alerts_in_its_views_and_scopes_review_map_and_contact(): void
    {
        $admin = User::factory()->create(['role' => 'fleet_admin']);
        $account = User::factory()->create(['role' => 'driver', 'company_id' => $admin->company_id]);
        $driver = Driver::forceCreate(['company_id' => $admin->company_id, 'user_id' => $account->id, 'full_name' => 'Ada Prueba']);
        $vehicle = Vehicle::forceCreate(['company_id' => $admin->company_id, 'plate' => 'TEST-123', 'brand' => 'Test', 'model' => 'Test']);
        VehicleAssignment::create(['company_id' => $admin->company_id, 'driver_id' => $driver->id, 'vehicle_id' => $vehicle->id,
            'started_at' => '2026-11-15 13:00:00', 'ended_at' => '2026-11-15 15:00:00']);
        $contact = EmergencyContact::forceCreate(['company_id' => $admin->company_id, 'driver_id' => $driver->id,
            'name' => 'Contacto reservado', 'relationship' => 'Familiar', 'phone' => '999000111']);
        $trip = $this->trip($account);
        $testEvent = $this->event($trip, 'SHARP_TURN');
        $sensorId = (string) Str::uuid();
        $testId = (string) Str::uuid();
        $realId = (string) Str::uuid();
        $base = "/api/v1/trips/{$trip->client_trip_id}/alerts";
        $headers = $this->headers($account);
        $this->putJson("/api/v1/trips/{$trip->client_trip_id}/events/$sensorId", [
            'origin' => 'SENSOR', 'type' => 'HARSH_BRAKING', 'occurred_at' => '2026-11-15T14:01:00.000Z',
            'latitude' => -16.25, 'longitude' => -71.5, 'location_origin' => 'GPS_NEO6M',
            'gps_received_at' => '2026-11-15T14:00:59.500Z', 'gps_age_ms' => 500,
        ], $headers)->assertCreated()->assertJsonPath('data.origin', 'SENSOR');
        $data = ['event_ids' => [$testEvent->client_event_id], 'occurred_at' => '2026-11-15T14:01:05.000Z',
            'test_only' => true, 'location_status' => 'AVAILABLE', 'latitude' => -16.25, 'longitude' => -71.5,
            'location_captured_at' => '2026-11-15T14:01:04.000Z'];
        $this->putJson("$base/$testId", $data, $headers)->assertCreated();
        $this->putJson("$base/$realId", array_replace($data, ['event_ids' => [$sensorId],
            'test_only' => false, 'location_status' => 'MISSING', 'latitude' => null, 'longitude' => null,
            'location_captured_at' => null]), $headers)->assertCreated();

        Auth::forgetGuards();
        $this->actingAs($admin, 'web')->get('/dashboard')->assertOk()->assertViewHas('pendingAlerts', 1);
        $this->get('/dashboard/events?tab=map&origin=SENSOR')->assertOk()->assertSee($sensorId)
            ->assertViewHas('geojson', fn ($data) => count($data['features']) === 1
                && $data['features'][0]['properties']['origin'] === 'SENSOR'
                && $data['features'][0]['geometry']['coordinates'] === [-71.5, -16.25]);
        $this->get('/dashboard/events?origin=SENSOR')->assertOk()->assertSee($sensorId)
            ->assertSee('Lectura del dispositivo');
        $this->get('/dashboard/trips/'.$trip->client_trip_id)->assertOk()->assertSee($sensorId)
            ->assertSee('Lectura del dispositivo');
        $this->get('/dashboard/notifications')->assertOk()->assertSee('Ada Prueba')->assertSee('TEST-123')
            ->assertSee('15/11/2026 09:01:05')->assertSee('Alerta de prueba')->assertDontSee($contact->phone);
        $this->get('/dashboard/events?kind=alerts&alert_status=pending')->assertOk()->assertSee($realId)->assertDontSee($testId);
        $this->get("/dashboard/alerts/$testId")->assertOk()->assertSee('Alerta de prueba')->assertSee($contact->phone)
            ->assertSee($testEvent->client_event_id)->assertSee('Ver en mapa');
        $this->get("/dashboard/events?kind=alerts&focus_alert=$testId")->assertOk()
            ->assertSee('15/11/2026 09:01:05')->assertDontSee($contact->phone)
            ->assertViewHas('geojson', fn ($data) => $data['features'][0]['geometry']['coordinates'] === [-71.5, -16.25]);
        $contact->delete();
        $this->get("/dashboard/alerts/$realId")->assertOk()->assertSee('Contacto no registrado')
            ->assertSee('Ubicación no disponible')->assertDontSee('Ver en mapa');
        $this->put("/dashboard/alerts/$realId/review")->assertRedirect("/dashboard/alerts/$realId");
        $this->get('/dashboard')->assertOk()->assertViewHas('pendingAlerts', 0);
        $this->assertSame($admin->id, ManeuverAlert::where('client_alert_id', $realId)->value('reviewed_by_user_id'));

        $foreign = User::factory()->create(['role' => 'fleet_admin']);
        $this->actingAs($foreign, 'web')->get('/dashboard/notifications')->assertOk()->assertDontSee($testId);
        $this->get("/dashboard/alerts/$testId")->assertNotFound();
        $this->get("/dashboard/events?kind=alerts&focus_alert=$testId")->assertNotFound();
        $this->put("/dashboard/alerts/$realId/review")->assertNotFound();
        $this->actingAs($account, 'web')->get("/dashboard/alerts/$testId")->assertForbidden();
    }
}
